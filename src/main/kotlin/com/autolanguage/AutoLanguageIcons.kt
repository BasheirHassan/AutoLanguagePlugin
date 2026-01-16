package com.autolanguage

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * كائن يحتوي على جميع الأيقونات المستخدمة في إضافة Auto Language
 * Object containing all icons used in the Auto Language plugin
 */
object AutoLanguageIcons {
    
    /**
     * أيقونة صغيرة جداً (16x16) - للقوائم الصغيرة والأزرار
     * Very small icon (16x16) - for small menus and buttons
     */
    val LOGO_16: Icon = IconLoader.getIcon("/icons/log-16.png", AutoLanguageIcons::class.java)
    
    /**
     * أيقونة متوسطة (32x32) - للأشرط الجانبية والأدوات
     * Medium icon (32x32) - for sidebars and tools
     */
    val LOGO_32: Icon = IconLoader.getIcon("/icons/log-32.png", AutoLanguageIcons::class.java)
    
    /**
     * أيقونة كبيرة (64x64) - الشعار الرئيسي للإضافة
     * Large icon (64x64) - main plugin icon
     */
    val LOGO_64: Icon = IconLoader.getIcon("/icons/log-64.png", AutoLanguageIcons::class.java)
    
    /**
     * أيقونة كبيرة جداً (128x128) - لصفحة الإضافات والشاشات الكبيرة
     * Very large icon (128x128) - for plugin page and large screens
     */
    val LOGO_128: Icon = IconLoader.getIcon("/icons/log-128.png", AutoLanguageIcons::class.java)
    
    /**
     * الحصول على الأيقونة المناسبة بناءً على الحجم المطلوب
     * Get the appropriate icon based on the requested size
     * 
     * @param size الحجم المطلوب (16, 32, 64, 128)
     * @return الأيقونة المناسبة
     */
    fun getIcon(size: Int): Icon {
        return when (size) {
            16 -> LOGO_16
            32 -> LOGO_32
            64 -> LOGO_64
            128 -> LOGO_128
            else -> LOGO_64 // الافتراضي: 64x64
        }
    }
}
