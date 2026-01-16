package com.autolanguage

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.win32.StdCallLibrary

interface User32 : StdCallLibrary {
    companion object {
        val INSTANCE: User32 = Native.load("user32", User32::class.java)
        const val WM_INPUTLANGCHANGEREQUEST = 0x0050
        const val KLF_ACTIVATE = 0x00000001
        const val KLF_SETFORPROCESS = 0x00000100
    }

    fun GetForegroundWindow(): HWND
    fun PostMessageA(hWnd: HWND, msg: Int, wParam: Long, lParam: Long): Boolean
    fun GetKeyboardLayoutList(nBuff: Int, lpList: LongArray): Int
    fun GetKeyboardLayout(idThread: Int): Long
    fun ActivateKeyboardLayout(hkl: Long, flags: Int): Long
}

object KeyboardSwitcher {
    private var arabicLayout: Long = 0
    private var englishLayout: Long = 0

    init {
        val layouts = LongArray(16)
        val count = User32.INSTANCE.GetKeyboardLayoutList(16, layouts)
        for (i in 0 until count) {
            val langId = layouts[i] and 0xFFFFL
            if (isArabicId(langId)) arabicLayout = layouts[i]
            if (isEnglishId(langId)) englishLayout = layouts[i]
        }
    }

    private fun isArabicId(id: Long): Boolean {
        // التحقق من أن معرف اللغة الأساسي هو 0x01 (العربية)
        return (id and 0x3FFL) == 0x01L
    }
    
    private fun isEnglishId(id: Long): Boolean {
        // التحقق من أن معرف اللغة الأساسي هو 0x09 (الإنجليزية)
        return (id and 0x3FFL) == 0x09L
    }

    fun switchToArabic() {
        if (arabicLayout != 0L && !isArabicLayout(getCurrentLayout())) {
            sendChangeRequest(arabicLayout)
        }
    }

    fun switchToEnglish() {
        if (englishLayout != 0L && !isEnglishLayout(getCurrentLayout())) {
            sendChangeRequest(englishLayout)
        }
    }

    fun getCurrentLayout(): Long {
        return User32.INSTANCE.GetKeyboardLayout(0)
    }

    fun isArabicLayout(layout: Long): Boolean {
        val langId = layout and 0xFFFFL
        return isArabicId(langId)
    }

    fun isEnglishLayout(layout: Long): Boolean {
        val langId = layout and 0xFFFFL
        return isEnglishId(langId)
    }

    private fun sendChangeRequest(layout: Long) {
        val hwnd = User32.INSTANCE.GetForegroundWindow()
        // إرسال رسالة لتبديل اللغة للنافذة النشطة
        User32.INSTANCE.PostMessageA(hwnd, User32.WM_INPUTLANGCHANGEREQUEST, 0L, layout)
        // تفعيل التخطيط للعملية الحالية أيضاً للتأكد
        User32.INSTANCE.ActivateKeyboardLayout(layout, User32.KLF_ACTIVATE)
    }
}
