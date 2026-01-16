package com.autolanguage

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "com.autolanguage.settings.AutoLanguageSettingsState",
    storages = [Storage("AutoLanguageSettings.xml")]
)
class AutoLanguageSettingsState : PersistentStateComponent<AutoLanguageSettingsState.State> {

    class State {
        var enabled: Boolean = true
        var showNotifications: Boolean = true
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): AutoLanguageSettingsState = project.getService(AutoLanguageSettingsState::class.java)
    }
}
