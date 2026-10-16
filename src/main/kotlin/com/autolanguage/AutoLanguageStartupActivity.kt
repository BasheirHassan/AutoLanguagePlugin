package com.autolanguage

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

// حالة مشتركة لكل مشروع (آمنة للخيوط)
object AutoLanguageStatus {
    private data class StatusInfo(
        var currentChar: Char = ' ',
        var language: String = "Ready",
        var status: String = "Waiting for input..."
    )

    private val projectStatuses = ConcurrentHashMap<Project, StatusInfo>()

    private fun getInfo(project: Project): StatusInfo {
        return projectStatuses.computeIfAbsent(project) { StatusInfo() }
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

    fun clear(project: Project) {
        projectStatuses.remove(project)
    }

    fun getCurrentLayoutName(): String {
        if (!KeyboardSwitcher.isWindows) return "N/A (Windows only)"
        val currentLayout = try {
            KeyboardSwitcher.getCurrentLayout()
        } catch (e: Throwable) {
            0L
        }
        if (currentLayout == 0L) return "Unknown"
        return if (KeyboardSwitcher.isArabicLayout(currentLayout)) "Arabic" else "English"
    }
}

class AutoLanguageStartupActivity : ProjectActivity {
    private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(AutoLanguageStartupActivity::class.java)

    @Volatile
    private var lastNotification: com.intellij.notification.Notification? = null

    override suspend fun execute(project: Project) {
        LOG.info("AutoLanguage: startup for project ${project.name}, windows=${KeyboardSwitcher.isWindows}")
        // تنظيف الحالة عند إغلاق المشروع لتجنب التسرب
        Disposer.register(project, com.intellij.openapi.Disposable {
            AutoLanguageStatus.clear(project)
            AutoLanguageWidgetHolder.remove(project)
        })

        if (!KeyboardSwitcher.isWindows) {
            AutoLanguageStatus.updateStatus(
                project, ' ', "Unsupported",
                "Windows only - keyboard switching disabled"
            )
            AutoLanguageWidgetHolder.updateWidget(project)
            return
        }

        val multicaster = EditorFactory.getInstance().eventMulticaster

        val caretListener = object : CaretListener {
            // لا نستدعي JNA هنا إطلاقاً أثناء التهيئة؛ تُملأ عند أول حدث
            private var lastLanguage: String? = null

            override fun caretPositionChanged(event: CaretEvent) {
                val editor = event.editor
                if (editor.project != project || project.isDisposed) return

                val settings = AutoLanguageSettingsState.getInstance()
                if (!settings.enabled) {
                    AutoLanguageStatus.updateStatus(project, ' ', "Disabled", "Plugin is disabled")
                    AutoLanguageWidgetHolder.updateWidget(project)
                    return
                }

                val offset = event.caret.offset
                val document = editor.document

                try {
                    val (detectedChar, detectedLang) = detectLanguageAround(document, offset)

                    if (detectedChar != null && detectedLang != "None") {
                        // قارن مع تخطيط النظام الفعلي، لا مع ذاكرة داخلية فقط،
                        // حتى لا نرسل أوامر تبديل زائدة وحتى نحترم تبديل المستخدم اليدوي
                        val actualLayout = AutoLanguageStatus.getCurrentLayoutName()
                        if (detectedLang != actualLayout && detectedLang != lastLanguage) {
                            val switched = when (detectedLang) {
                                "Arabic" -> KeyboardSwitcher.switchToArabic()
                                "English" -> KeyboardSwitcher.switchToEnglish()
                                else -> false
                            }
                            val message = if (switched) {
                                "Language: $detectedLang"
                            } else {
                                "Detected: $detectedLang (layout unavailable)"
                            }
                            LOG.info(
                                "AutoLanguage: caret detected=$detectedLang char='$detectedChar' actual=$actualLayout switched=$switched"
                            )
                            AutoLanguageStatus.updateStatus(project, detectedChar, detectedLang, message)
                            if (switched) showLanguageNotification(project, detectedLang)
                            lastLanguage = detectedLang
                        } else if (detectedLang != lastLanguage) {
                            // اللغة المكتشفة مطابقة للنظام، حدّث العرض فقط
                            AutoLanguageStatus.updateStatus(
                                project, detectedChar, detectedLang, "Language: $detectedLang"
                            )
                            lastLanguage = detectedLang
                        }
                    } else {
                        AutoLanguageStatus.updateStatus(project, ' ', "None", "Searching...")
                    }

                    AutoLanguageWidgetHolder.updateWidget(project)
                } catch (e: Exception) {
                    LOG.warn("AutoLanguage: caret handler failed", e)
                    AutoLanguageStatus.updateStatus(project, ' ', "Error", e.message ?: "Error")
                    AutoLanguageWidgetHolder.updateWidget(project)
                }
            }
        }

        multicaster.addCaretListener(caretListener, project)
    }

    private fun detectLanguageAround(
        document: com.intellij.openapi.editor.Document,
        offset: Int
    ): Pair<Char?, String> {
        // قراءة واحدة بدون تخصيص TextRange متكرر
        val seq: CharSequence = document.charsSequence
        val textLength = seq.length

        // الحرف السابق (الأكثر احتمالاً لأنه المكتوب للتو)
        if (offset > 0 && offset <= textLength) {
            val c = seq[offset - 1]
            if (!c.isWhitespace()) {
                if (isArabic(c)) return c to "Arabic"
                if (isEnglish(c)) return c to "English"
            }
        }

        // الحرف التالي
        if (offset < textLength) {
            val c = seq[offset]
            if (!c.isWhitespace()) {
                if (isArabic(c)) return c to "Arabic"
                if (isEnglish(c)) return c to "English"
            }
        }

        // بحث موسع حتى 10 أحرف
        for (i in 1..10) {
            val p = offset - 1 - i
            if (p >= 0 && p < textLength) {
                val c = seq[p]
                if (!c.isWhitespace()) {
                    if (isArabic(c)) return c to "Arabic"
                    if (isEnglish(c)) return c to "English"
                }
            }
            val n = offset + i
            if (n >= 0 && n < textLength) {
                val c = seq[n]
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

            val flagEmoji = if (language == "Arabic") "🇸🇦" else "🇺🇸"

            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("Auto Language")
                .createNotification(
                    "Language Switched",
                    "Keyboard layout changed to $language $flagEmoji",
                    NotificationType.INFORMATION
                )

            notification.setIcon(AutoLanguageIcons.LOGO_32)

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

    override fun getDisplayName(): String = "Auto Language"

    override fun isAvailable(project: Project): Boolean = true

    override fun disposeWidget(widget: StatusBarWidget) {
        if (widget is AutoLanguageStatusBarWidget) {
            AutoLanguageWidgetHolder.removeWidget(widget)
        }
    }

    override fun getId(): String = "AutoLanguageStatusBarWidget"
}

class AutoLanguageStatusBarWidget(private val project: Project) : CustomStatusBarWidget {
    private val panel = JPanel()
    private val iconLabel = JLabel()
    private val textLabel = JLabel()

    init {
        iconLabel.icon = AutoLanguageIcons.LOGO_16
        iconLabel.toolTipText = "Auto Language - click to open settings"

        textLabel.toolTipText = "Auto Language Status - click to open settings"

        panel.add(iconLabel)
        panel.add(textLabel)
        panel.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)

        // النقر على الودجت يفتح إعدادات الإضافة مباشرة
        val openSettings = object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (project.isDisposed) return
                try {
                    com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, AutoLanguageConfigurable::class.java)
                } catch (t: Throwable) {
                    com.intellij.openapi.diagnostic.Logger
                        .getInstance(AutoLanguageStatusBarWidget::class.java)
                        .warn("AutoLanguage: open settings failed", t)
                    com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, "tools")
                }
            }
        }
        panel.addMouseListener(openSettings)
        iconLabel.addMouseListener(openSettings)
        textLabel.addMouseListener(openSettings)

        updateDisplay()
    }

    override fun install(statusBar: com.intellij.openapi.wm.StatusBar) {
        updateDisplay()
    }

    override fun dispose() {}

    override fun ID(): String = "AutoLanguageStatusBarWidget"

    override fun getComponent(): JComponent {
        return panel
    }

    fun updateDisplay() {
        val settings = AutoLanguageSettingsState.getInstance()
        if (!settings.enabled || !settings.showStatusBar) {
            panel.isVisible = false
            return
        }
        panel.isVisible = true

        val (currentChar, language, status) = AutoLanguageStatus.getStatus(project)
        val charDisplay = if (currentChar == ' ') " " else "  [$currentChar]"
        val layoutName = AutoLanguageStatus.getCurrentLayoutName()

        iconLabel.icon = AutoLanguageIcons.LOGO_16

        textLabel.text = "Auto Language $charDisplay"

        panel.toolTipText = """
            Auto Language Status (click to open settings)
            Status: $status
            Detected Character: $charDisplay
            Detected Language: $language
            Current Layout: $layoutName
        """.trimIndent()

        panel.revalidate()
        panel.repaint()
    }
}

// مراجع الـ widgets لكل مشروع (آمنة للخيوط، والتحديث دائماً على EDT)
object AutoLanguageWidgetHolder {
    private val widgets = ConcurrentHashMap<Project, AutoLanguageStatusBarWidget>()

    fun setWidget(project: Project, widget: AutoLanguageStatusBarWidget) {
        widgets[project] = widget
    }

    fun removeWidget(widget: AutoLanguageStatusBarWidget) {
        val project = widgets.entries.find { it.value == widget }?.key
        if (project != null) {
            widgets.remove(project)
        }
    }

    fun remove(project: Project) {
        widgets.remove(project)
    }

    fun updateWidget(project: Project) {
        val widget = widgets[project] ?: return
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            if (!project.isDisposed) widget.updateDisplay()
        } else {
            app.invokeLater({
                if (!project.isDisposed) widget.updateDisplay()
            })
        }
    }
}
