package com.autolanguage

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.builder.bindSelected
import javax.swing.JComponent

class AutoLanguageConfigurable(private val project: Project) : Configurable {

    private val settings = AutoLanguageSettingsState.getInstance(project)
    private var enabled = settings.state.enabled
    private var showNotifications = settings.state.showNotifications

    override fun getDisplayName(): String = "Auto Language Switcher"

    override fun createComponent(): JComponent {
        return panel {
            group("General Settings") {
                lateinit var enabledCheckBox: com.intellij.ui.dsl.builder.Cell<javax.swing.JCheckBox>
                row {
                    enabledCheckBox = checkBox("Enable Automatic Language Switching")
                        .bindSelected(
                            { enabled },
                            { value -> enabled = value }
                        )
                }
                row {
                    checkBox("Show notification when language changes")
                        .bindSelected(
                            { showNotifications },
                            { value -> showNotifications = value }
                        ).enabledIf(enabledCheckBox.selected)
                }
            }
        }
    }

    override fun isModified(): Boolean {
        return enabled != settings.state.enabled ||
               showNotifications != settings.state.showNotifications
    }

    override fun apply() {
        settings.state.enabled = enabled
        settings.state.showNotifications = showNotifications
    }

    override fun reset() {
        enabled = settings.state.enabled
        showNotifications = settings.state.showNotifications
    }
}
