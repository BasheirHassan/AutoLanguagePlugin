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
    fun PostMessageA(hWnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): Boolean
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
        // جميع معرفات اللغة العربية الممكنة
        return id == 0x0401L || // العربية (السعودية)
               id == 0x1401L || // العربية (الأردن)
               id == 0x1801L || // العربية (الإمارات)
               id == 0x2001L || // العربية (مصر)
               id == 0x0C01L || // العربية (لبنان)
               id == 0x2801L || // العربية (الكويت)
               id == 0x2C01L || // العربية (البحرين)
               id == 0x2401L || // العربية (قطر)
               id == 0x1001L || // العربية (الجزائر)
               id == 0x0801L || // العربية (العراق)
               id == 0x1C01L || // العربية (ليبيا)
               id == 0x3401L || // العربية (تونس)
               id == 0x3801L || // العربية (عمان)
               id == 0x3C01L || // العربية (السودان)
               id == 0x3001L    // العربية (سوريا)
    }
    
    private fun isEnglishId(id: Long): Boolean = id == 0x0409L || id == 0x0809L || id == 0x0C09L || id == 0x1009L || id == 0x1409L

    fun switchToArabic() {
        if (arabicLayout != 0L) sendChangeRequest(arabicLayout)
    }

    fun switchToEnglish() {
        if (englishLayout != 0L) sendChangeRequest(englishLayout)
    }

    fun getCurrentLayout(): Long {
        return User32.INSTANCE.GetKeyboardLayout(0)
    }

    fun isArabicLayout(layout: Long): Boolean {
        val langId = layout and 0xFFFFL
        return isArabicId(langId)
    }

    private fun sendChangeRequest(layout: Long) {
        User32.INSTANCE.ActivateKeyboardLayout(layout, User32.KLF_ACTIVATE or User32.KLF_SETFORPROCESS)
    }
}
