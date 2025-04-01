// src/main/kotlin/com/github/peanut996/codeprompthelper/settings/PresetService.kt
package com.github.peanut996.codeprompthelper.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.github.peanut996.codeprompthelper.Preset
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Service responsible for storing and retrieving the list of prompt presets.
 * Uses PersistentStateComponent to save settings in codePromptHelperPresets.xml.
 */
@State(
    name = "CodePromptHelperPresets", // State name used in the XML file
    storages = [Storage("codePromptHelperPresets.xml")] // Filename in the IDE's config/options directory
)
class PresetService : PersistentStateComponent<PresetService.State> {

    // Internal class holding the actual state (list of presets)
    // Needs to be public or internal for serialization
    class State {
        // Use mutable list for easier modification in settings
        var presets: MutableList<Preset> = mutableListOf(
            // Default presets when the plugin is first installed
            Preset(name = "Explain Code", prefix = "Explain the following code:\n```\n", suffix = "\n```"),
            Preset(name = "Refactor Code", prefix = "Refactor the following code to improve readability:\n```\n", suffix = "\n```"),
            Preset(name = "Add Tests", prefix = "Write unit tests for the following code:\n```\n", suffix = "\n```"),
            Preset(name = "Translate to Python", prefix = "Translate the following code to Python:\n```\n", suffix = "\n```"),
            Preset(name = "Find Bugs", prefix = "Find potential bugs in the following code:\n```\n", suffix = "\n```")
        )
    }

    private var internalState = State()

    /**
     * Returns an immutable list of the current presets.
     */
    fun getPresets(): List<Preset> {
        // Return a defensive copy to prevent external modification
        return internalState.presets.map { it.copy() }
    }

    /**
     * Sets the list of presets. Typically called from the settings UI upon applying changes.
     */
    fun setPresets(newPresets: List<Preset>) {
        // Create a new State object to ensure change detection by PersistentStateComponent
        val newState = State()
        newState.presets = newPresets.map { it.copy() }.toMutableList() // Store copies
        internalState = newState
    }

    // --- PersistentStateComponent implementation ---

    /**
     * Returns the current state object to be serialized.
     */
    override fun getState(): State {
        return internalState
    }

    /**
     * Loads the state from the XML file upon IDE startup.
     * @param state The deserialized state.
     */
    override fun loadState(state: State) {
        // Use XmlSerializerUtil for robust state loading
        XmlSerializerUtil.copyBean(state, internalState)
    }

    // --- Companion object for easy service access ---
    companion object {
        /**
         * Gets the instance of the PresetService.
         */
        fun getInstance(): PresetService {
            return ApplicationManager.getApplication().getService(PresetService::class.java)
                ?: throw IllegalStateException("PresetService not found. Check plugin.xml registration.")
        }
    }
}