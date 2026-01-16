package com.autolanguage

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.selected

class AutoLanguageConfigurable : BoundConfigurable("Auto Language Switcher") {

    private val settings = AutoLanguageSettingsState.getInstance()

    override fun createPanel(): DialogPanel {
        return panel {
            group("General Settings") {
                lateinit var enabledCheckBox: com.intellij.ui.dsl.builder.Cell<javax.swing.JCheckBox>
                row {
                    enabledCheckBox = checkBox("Enable Automatic Language Switching")
                        .bindSelected(settings::enabled)
                }
                row {
                    checkBox("Show notification when language changes")
                        .bindSelected(settings::showNotifications)
                        .enabledIf(enabledCheckBox.selected)
                }
            }
        }
    }

    override fun apply() {
        super.apply()
        // تحديث جميع الودجات في جميع المشاريع المفتوحة فور تطبيق الإعدادات
        com.intellij.openapi.project.ProjectManager.getInstance().openProjects.forEach { project ->
            AutoLanguageWidgetHolder.updateWidget(project)
        }
    }
}
