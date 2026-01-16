package com.autolanguage

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "com.autolanguage.settings.AutoLanguageSettingsState",
    storages = [Storage("AutoLanguageSettings.xml")]
)
class AutoLanguageSettingsState : PersistentStateComponent<AutoLanguageSettingsState> {

    var enabled: Boolean = true
    var showNotifications: Boolean = true

    override fun getState(): AutoLanguageSettingsState = this

    override fun loadState(state: AutoLanguageSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): AutoLanguageSettingsState = com.intellij.openapi.application.ApplicationManager.getApplication().getService(AutoLanguageSettingsState::class.java)
    }
}
