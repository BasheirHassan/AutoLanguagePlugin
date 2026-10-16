package com.autolanguage

import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.StdCallLibrary

interface User32 : StdCallLibrary {
    companion object {
        const val WM_INPUTLANGCHANGEREQUEST = 0x0050
        const val KLF_ACTIVATE = 0x00000001
        const val KLF_SETFORPROCESS = 0x00000100
    }

    fun GetForegroundWindow(): HWND?
    fun PostMessageA(hWnd: HWND?, msg: Int, wParam: Long, lParam: Long): Boolean
    fun GetKeyboardLayoutList(nBuff: Int, lpList: LongArray): Int
    fun GetKeyboardLayout(idThread: Int): Long
    fun ActivateKeyboardLayout(hkl: Long, flags: Int): Long
    fun GetWindowThreadProcessId(hWnd: HWND?, lpdwProcessId: IntArray?): Int
}

object KeyboardSwitcher {
    private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(KeyboardSwitcher::class.java)

    val isWindows: Boolean =
        System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)

    /**
     * JNA داخل إضافة IntelliJ لا يجد jnidispatch.dll لوحده، لأن كلاس Native يُحمَّل من
     * classloader المنصة (util-8.jar) الذي لا يحوي المورّد الأصلي. لذلك نستخرج الـDLL
     * المضمّن في jna-5.16.0.jar (داخل lib الإضافة) إلى مجلد مؤقت ونوجّه JNA إليه
     * عبر jna.boot.library.path قبل أول استخدام.
     */
    private fun bootstrapJnaNative(): Boolean {
        return try {
            val arch = System.getProperty("os.arch", "")
            val resourcePrefix = when {
                arch.contains("aarch64", ignoreCase = true) -> "win32-aarch64"
                arch.contains("64", ignoreCase = true) -> "win32-x86-64"
                else -> "win32-x86"
            }
            val resPath = "/com/sun/jna/$resourcePrefix/jnidispatch.dll"
            val input = KeyboardSwitcher::class.java.getResourceAsStream(resPath)
                ?: run {
                    LOG.warn("AutoLanguage: bundled jnidispatch not found at $resPath")
                    return false
                }
            val bytes = input.use { it.readBytes() }
            val dir = java.nio.file.Paths.get(
                System.getProperty("java.io.tmpdir"), "autolanguage-jna", resourcePrefix
            )
            java.nio.file.Files.createDirectories(dir)
            val dll = dir.resolve("jnidispatch.dll")
            val existing = try {
                java.nio.file.Files.size(dll)
            } catch (e: Throwable) {
                -1L
            }
            if (existing != bytes.size.toLong()) {
                try {
                    java.nio.file.Files.write(dll, bytes)
                } catch (e: java.nio.file.FileAlreadyExistsException) {
                    // الـDLL محمّل حالياً من تشغيل سابق، نعيد استخدامه كما هو
                }
            }
            System.setProperty("jna.boot.library.path", dir.toString())
            LOG.info("AutoLanguage: jna.boot.library.path=$dir (${bytes.size} bytes)")
            true
        } catch (e: Throwable) {
            LOG.warn("AutoLanguage: JNA bootstrap failed", e)
            false
        }
    }

    // تحميل كسول ومحمي: لا يلمس user32 إطلاقاً على غير Windows
    private val user32: User32? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (!isWindows) {
            null
        } else {
            bootstrapJnaNative()
            try {
                Native.load("user32", User32::class.java)
            } catch (e: Throwable) {
                LOG.warn("AutoLanguage: Native.load(user32) failed", e)
                null
            }
        }
    }

    private var arabicLayout: Long = 0L
    private var englishLayout: Long = 0L
    private var layoutsLoaded: Boolean = false

    @Synchronized
    fun refreshLayouts() {
        val u = user32 ?: return
        try {
            val layouts = LongArray(16)
            val count = u.GetKeyboardLayoutList(16, layouts)
            var ar = 0L
            var en = 0L
            for (i in 0 until count) {
                val langId = layouts[i] and 0xFFFFL
                if (ar == 0L && isArabicId(langId)) ar = layouts[i]
                if (en == 0L && isEnglishId(langId)) en = layouts[i]
            }
            arabicLayout = ar
            englishLayout = en
            layoutsLoaded = true
            LOG.info(
                "AutoLanguage: found $count layouts, arabic=${ar.toString(16)} english=${en.toString(16)}"
            )
        } catch (e: Throwable) {
            LOG.warn("AutoLanguage: refreshLayouts failed", e)
            // تجاهل آمن: يبقى التبديل معطلاً بدل كسر الإضافة
        }
    }

    private fun ensureLayouts() {
        if (!layoutsLoaded) refreshLayouts()
    }

    private fun isArabicId(id: Long): Boolean {
        // المعرف الأساسي للعربية هو 0x01 (أول 10 بت من LANGID)
        return (id and 0x3FFL) == 0x01L
    }

    private fun isEnglishId(id: Long): Boolean {
        // المعرف الأساسي للإنجليزية هو 0x09
        return (id and 0x3FFL) == 0x09L
    }

    fun switchToArabic(): Boolean {
        if (!isWindows) return false
        ensureLayouts()
        val target = arabicLayout
        if (target == 0L) {
            LOG.warn("AutoLanguage: arabic layout not installed, switch aborted")
            return false
        }
        if (isArabicLayout(getCurrentLayout())) return true
        return sendChangeRequest(target)
    }

    fun switchToEnglish(): Boolean {
        if (!isWindows) return false
        ensureLayouts()
        val target = englishLayout
        if (target == 0L) {
            LOG.warn("AutoLanguage: english layout not installed, switch aborted")
            return false
        }
        if (isEnglishLayout(getCurrentLayout())) return true
        return sendChangeRequest(target)
    }

    fun getCurrentLayout(): Long {
        val u = user32 ?: return 0L
        return try {
            val hwnd = try {
                u.GetForegroundWindow()
            } catch (e: Throwable) {
                null
            }
            if (hwnd != null) {
                val threadId = try {
                    u.GetWindowThreadProcessId(hwnd, null)
                } catch (e: Throwable) {
                    0
                }
                if (threadId != 0) u.GetKeyboardLayout(threadId) else u.GetKeyboardLayout(0)
            } else {
                u.GetKeyboardLayout(0)
            }
        } catch (e: Throwable) {
            0L
        }
    }

    fun isArabicLayout(layout: Long): Boolean {
        if (layout == 0L) return false
        val langId = layout and 0xFFFFL
        return isArabicId(langId)
    }

    fun isEnglishLayout(layout: Long): Boolean {
        if (layout == 0L) return false
        val langId = layout and 0xFFFFL
        return isEnglishId(langId)
    }

    private fun sendChangeRequest(layout: Long): Boolean {
        val u = user32 ?: return false
        return try {
            val hwnd = try {
                u.GetForegroundWindow()
            } catch (e: Throwable) {
                null
            }
            val posted = if (hwnd != null) {
                u.PostMessageA(hwnd, User32.WM_INPUTLANGCHANGEREQUEST, 0L, layout)
            } else {
                LOG.warn("AutoLanguage: no foreground window, PostMessage skipped")
                false
            }
            // تفعيل التخطيط للعملية الحالية للتأكيد
            u.ActivateKeyboardLayout(layout, User32.KLF_ACTIVATE)
            LOG.info(
                "AutoLanguage: switch request layout=${layout.toString(16)} posted=$posted"
            )
            true
        } catch (e: Throwable) {
            LOG.warn("AutoLanguage: sendChangeRequest failed", e)
            false
        }
    }
}
