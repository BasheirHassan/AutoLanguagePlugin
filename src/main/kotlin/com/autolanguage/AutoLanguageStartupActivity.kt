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
    override suspend fun execute(project: Project) {
        val multicaster = EditorFactory.getInstance().eventMulticaster
        
        val caretListener = object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                val editor = event.editor
                if (editor.project != project) return
                
                val offset = event.caret?.offset ?: return
                val document = editor.document
                val textLength = document.textLength
                
                try {
                    var detectedChar: Char? = null
                    
                    // 1. Check character BEFORE cursor
                    if (offset > 0) {
                        val charBefore = document.getText(TextRange(offset - 1, offset))[0]
                        if (!charBefore.isWhitespace()) {
                            detectedChar = charBefore
                        }
                    }
                    
                    // 2. If not found, check character AFTER cursor
                    if (detectedChar == null && offset < textLength) {
                        val charAfter = document.getText(TextRange(offset, offset + 1))[0]
                        if (!charAfter.isWhitespace()) {
                            detectedChar = charAfter
                        }
                    }
                    
                    // 3. Fallback: Search a bit wider (up to 5 characters in both directions)
                    if (detectedChar == null) {
                        for (i in 1..5) {
                            val prev = offset - i
                            if (prev >= 0) {
                                val c = document.getText(TextRange(prev, prev + 1))[0]
                                if (!c.isWhitespace()) {
                                    detectedChar = c
                                    break
                                }
                            }
                            val next = offset + i
                            if (next < textLength) {
                                val c = document.getText(TextRange(next, next + 1))[0]
                                if (!c.isWhitespace()) {
                                    detectedChar = c
                                    break
                                }
                            }
                        }
                    }

                    val char = detectedChar ?: ' '
                    
                    if (detectedChar != null) {
                        if (isArabic(char)) {
                            KeyboardSwitcher.switchToArabic()
                            AutoLanguageStatus.updateStatus(project, char, "Arabic", "Switched to Arabic")
                        } else if (isEnglish(char)) {
                            KeyboardSwitcher.switchToEnglish()
                            AutoLanguageStatus.updateStatus(project, char, "English", "Switched to English")
                        } else {
                            AutoLanguageStatus.updateStatus(project, char, "Neutral", "Neutral character detected")
                        }
                    } else {
                        AutoLanguageStatus.updateStatus(project, ' ', "None", "No character near cursor")
                    }
                    
                    // تحديث شريط الحالة
                    AutoLanguageWidgetHolder.updateWidget(project)
                } catch (e: Exception) {
                    AutoLanguageStatus.updateStatus(project, ' ', "Error", e.message ?: "Unknown error")
                    AutoLanguageWidgetHolder.updateWidget(project)
                }
            }
        }
        
        multicaster.addCaretListener(caretListener, project)
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
