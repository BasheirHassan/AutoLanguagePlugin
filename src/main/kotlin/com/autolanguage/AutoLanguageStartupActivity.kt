package com.autolanguage

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.WindowManager
import javax.swing.JComponent
import javax.swing.JLabel
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager

// كائن مشترك لتخزين الحالة
// كائن مشترك لتخزين الحالة لكل مشروع
object AutoLanguageStatus {
    private data class StatusInfo(
        var currentChar: Char = ' ',
        var language: String = "Ready",
        var status: String = "Waiting for input..."
    )

    private val projectStatuses = mutableMapOf<Project, StatusInfo>()

    private fun getInfo(project: Project): StatusInfo {
        return projectStatuses.getOrPut(project) { StatusInfo() }
    }

    fun updateStatus(project: Project, char: Char, lang: String, message: String) {
        val info = getInfo(project)
        info.currentChar = char
        info.language = lang
        info.status = message
    }

    fun getStatus(project: Project): Triple<Char, String, String> {
        val info = getInfo(project)
        return Triple(info.currentChar, info.language, info.status)
    }

    fun getCurrentLayoutName(): String {
        val currentLayout = KeyboardSwitcher.getCurrentLayout()
        return if (KeyboardSwitcher.isArabicLayout(currentLayout)) "Arabic" else "English"
    }
}

class AutoLanguageStartupActivity : ProjectActivity {
    private var lastNotification: com.intellij.notification.Notification? = null

    override suspend fun execute(project: Project) {
        val multicaster = EditorFactory.getInstance().eventMulticaster
        
        val caretListener = object : CaretListener {
            private var lastLanguage: String? = AutoLanguageStatus.getCurrentLayoutName()

            override fun caretPositionChanged(event: CaretEvent) {
                val editor = event.editor
                if (editor.project != project) return
                
                val settings = AutoLanguageSettingsState.getInstance()
                if (!settings.enabled) {
                    AutoLanguageStatus.updateStatus(project, ' ', "Disabled", "Plugin is disabled")
                    AutoLanguageWidgetHolder.updateWidget(project)
                    return
                }

                val offset = event.caret?.offset ?: return
                val document = editor.document
                
                try {
                    val (detectedChar, detectedLang) = detectLanguageAround(document, offset)
                    
                    if (detectedChar != null && detectedLang != "None") {
                        if (detectedLang != lastLanguage) {
                            when (detectedLang) {
                                "Arabic" -> {
                                    KeyboardSwitcher.switchToArabic()
                                    AutoLanguageStatus.updateStatus(project, detectedChar, "Arabic", "Language: Arabic")
                                    showLanguageNotification(project, "Arabic")
                                }
                                "English" -> {
                                    KeyboardSwitcher.switchToEnglish()
                                    AutoLanguageStatus.updateStatus(project, detectedChar, "English", "Language: English")
                                    showLanguageNotification(project, "English")
                                }
                            }
                            lastLanguage = detectedLang
                        }
                    } else {
                        AutoLanguageStatus.updateStatus(project, ' ', "None", "Searching...")
                    }
                    
                    // تحديث شريط الحالة
                    AutoLanguageWidgetHolder.updateWidget(project)
                } catch (e: Exception) {
                    AutoLanguageStatus.updateStatus(project, ' ', "Error", e.message ?: "Error")
                    AutoLanguageWidgetHolder.updateWidget(project)
                }
            }
        }
        
        multicaster.addCaretListener(caretListener, project)
    }

    private fun detectLanguageAround(document: com.intellij.openapi.editor.Document, offset: Int): Pair<Char?, String> {
        val textLength = document.textLength
        
        // فحص مباشر: الحرف السابق (الأكثر احتمالاً لأنه المكتوب للتو)
        if (offset > 0) {
            val c = document.getText(TextRange(offset - 1, offset))[0]
            if (!c.isWhitespace()) {
                if (isArabic(c)) return c to "Arabic"
                if (isEnglish(c)) return c to "English"
            }
        }
        
        // فحص مباشر: الحرف التالي
        if (offset < textLength) {
            val c = document.getText(TextRange(offset, offset + 1))[0]
            if (!c.isWhitespace()) {
                if (isArabic(c)) return c to "Arabic"
                if (isEnglish(c)) return c to "English"
            }
        }
        
        // بحث موسع حتى 10 أحرف
        for (i in 1..10) {
            // للخلف
            val p = offset - i
            if (p >= 0) {
                val c = document.getText(TextRange(p, p + 1))[0]
                if (!c.isWhitespace()) {
                    if (isArabic(c)) return c to "Arabic"
                    if (isEnglish(c)) return c to "English"
                }
            }
            // للأمام
            val n = offset + i - 1
            if (n < textLength) {
                val c = document.getText(TextRange(n, n + 1))[0]
                if (!c.isWhitespace()) {
                    if (isArabic(c)) return c to "Arabic"
                    if (isEnglish(c)) return c to "English"
                }
            }
        }
        
        return null to "None"
    }

    private fun isArabic(c: Char): Boolean {
        // نطاق الأحرف العربية الموسع
        return c.code in 0x0600..0x06FF ||
               c.code in 0x0750..0x077F ||
               c.code in 0x08A0..0x08FF ||
               c.code in 0xFB50..0xFDFF ||
               c.code in 0xFE70..0xFEFF
    }
    
    private fun isEnglish(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

    private fun showLanguageNotification(project: Project, language: String) {
        val settings = AutoLanguageSettingsState.getInstance()
        if (!settings.showNotifications) return

        ApplicationManager.getApplication().invokeLater {
            // إغلاق الإشعار السابق إذا كان موجوداً لتجنب التراكم
            lastNotification?.expire()

            val icon = if (language == "Arabic") "🇸🇦" else "🇺🇸"
            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("Auto Language Switcher")
                .createNotification(
                    "Language Switched",
                    "Keyboard layout changed to $language $icon",
                    NotificationType.INFORMATION
                )
            
            notification.notify(project)
            lastNotification = notification

            // إخفاء الإشعار تلقائياً بعد ثانيتين
            com.intellij.util.concurrency.AppExecutorUtil.getAppScheduledExecutorService().schedule({
                notification.expire()
            }, 2, java.util.concurrent.TimeUnit.SECONDS)
        }
    }
}

class AutoLanguageStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun createWidget(project: Project): StatusBarWidget {
        val widget = AutoLanguageStatusBarWidget(project)
        AutoLanguageWidgetHolder.setWidget(project, widget)
        return widget
    }

    override fun getDisplayName(): String = "Auto Language Switcher"

    override fun isAvailable(project: Project): Boolean = true

    override fun disposeWidget(widget: StatusBarWidget) {
        if (widget is AutoLanguageStatusBarWidget) {
            AutoLanguageWidgetHolder.removeWidget(widget)
        }
    }

    override fun getId(): String = "AutoLanguageStatusBarWidget"
}

class AutoLanguageStatusBarWidget(private val project: Project) : CustomStatusBarWidget {
    private val label = JLabel()
    private var statusBar: com.intellij.openapi.wm.StatusBar? = null

    init {
        updateDisplay()
    }

    override fun install(statusBar: com.intellij.openapi.wm.StatusBar) {
        this.statusBar = statusBar
        updateDisplay()
    }

    override fun dispose() {}

    override fun ID(): String = "AutoLanguageStatusBarWidget"

    override fun getComponent(): JComponent {
        return label
    }

    fun updateDisplay() {
        val settings = AutoLanguageSettingsState.getInstance()
        if (!settings.enabled || !settings.showStatusBar) {
            label.isVisible = false
            return
        }
        label.isVisible = true
        
        val (currentChar, language, status) = AutoLanguageStatus.getStatus(project)
        val charDisplay = if (currentChar == ' ') " " else "'$currentChar'"
        val layoutName = AutoLanguageStatus.getCurrentLayoutName()
        
        label.text = "🔄 Auto Language: $status | Detected: $charDisplay | Current: $layoutName"
        label.toolTipText = """
            Auto Language Switcher Status
            Status: $status
            Detected Character: $charDisplay
            Detected Language: $language
            Current Layout: $layoutName
        """.trimIndent()
        
        label.revalidate()
        label.repaint()
    }
}

// كائن مشترك لتخزين مراجع الـ widgets لكل مشروع
object AutoLanguageWidgetHolder {
    private val widgets = mutableMapOf<Project, AutoLanguageStatusBarWidget>()

    fun setWidget(project: Project, widget: AutoLanguageStatusBarWidget) {
        widgets[project] = widget
    }

    fun removeWidget(widget: AutoLanguageStatusBarWidget) {
        val project = widgets.entries.find { it.value == widget }?.key
        if (project != null) {
            widgets.remove(project)
        }
    }

    fun updateWidget(project: Project) {
        widgets[project]?.updateDisplay()
    }
}
