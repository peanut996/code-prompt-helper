// src/main/kotlin/com/github/peanut996/codeprompthelper/settings/PresetConfigurable.kt
package com.github.peanut996.codeprompthelper.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.util.NlsContexts
import javax.swing.JComponent

/**
 * Provides the configuration interface for managing presets in the IDE settings.
 * Implements the Configurable interface from the IntelliJ Platform.
 */
class PresetConfigurable : Configurable {

    // Holds the instance of the settings UI panel
    private var settingsPanel: PresetSettingsPanel? = null

    /**
     * Creates the Swing component for the settings page.
     * This is called by the IDE when the settings page is displayed.
     * @return The JComponent representing the settings UI.
     */
    override fun createComponent(): JComponent? {
        settingsPanel = PresetSettingsPanel()
        // Load the initial presets when the component is created
        settingsPanel?.resetFrom(PresetService.getInstance().getPresets())
        return settingsPanel?.panel
    }

    /**
     * Checks if the settings in the UI have been modified compared to the stored settings.
     * Called by the IDE to determine if the 'Apply' button should be enabled.
     * @return True if settings have been modified, false otherwise.
     */
    override fun isModified(): Boolean {
        val modified = settingsPanel?.isModified(PresetService.getInstance().getPresets()) ?: false
        println("PresetConfigurable.isModified: $modified")
        return modified
    }

    /**
     * Saves the modified settings from the UI panel to the PresetService.
     * Called by the IDE when the user clicks 'Apply' or 'OK'.
     * @throws ConfigurationException if settings are invalid (not used here).
     */
    @Throws(ConfigurationException::class)
    override fun apply() {
        println("PresetConfigurable.apply called")
        settingsPanel?.applyTo(PresetService.getInstance())
    }

    /**
     * Resets the UI panel to reflect the currently stored settings.
     * Called by the IDE when the user clicks 'Reset' or initially opens the settings page.
     */
    override fun reset() {
        println("PresetConfigurable.reset called")
        settingsPanel?.resetFrom(PresetService.getInstance().getPresets())
    }

    /**
     * Returns the display name for the settings page.
     * This name appears in the settings navigation tree.
     * @return The display name.
     */
    override fun getDisplayName(): @NlsContexts.ConfigurableName String {
        return "Code Prompt Helper Presets"
    }

    /**
     * Disposes of the UI resources when the settings page is closed.
     * Helps prevent memory leaks.
     */
    override fun disposeUIResources() {
        println("PresetConfigurable.disposeUIResources called")
        settingsPanel = null // Release the reference to the panel
    }
}