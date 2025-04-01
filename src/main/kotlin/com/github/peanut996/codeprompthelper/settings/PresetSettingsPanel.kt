// src/main/kotlin/com/github/peanut996/codeprompthelper/settings/PresetSettingsPanel.kt
package com.github.peanut996.codeprompthelper.settings

import com.github.peanut996.codeprompthelper.Preset
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.* // Import necessary DSL builders
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.event.ListSelectionEvent

/**
 * Creates the UI panel for managing presets in the IDE settings.
 * Uses Kotlin UI DSL for layout.
 */
class PresetSettingsPanel {

    // Model for the JList holding the presets
    private val listModel = CollectionListModel<Preset>()
    // The list component itself
    private val presetList = JBList(listModel)

    // UI components for editing the selected preset
    private val nameField = JBTextField()
    private val prefixArea = JBTextArea().apply { lineWrap = true; wrapStyleWord = true }
    private val suffixArea = JBTextArea().apply { lineWrap = true; wrapStyleWord = true }

    // Store the original state for change detection
    private var originalPresets: List<Preset> = emptyList()

    // The main panel built using Kotlin UI DSL
    val panel: DialogPanel = panel {
        // Row containing the list and the editor form
        row {
            // Cell for the preset list with toolbar decorator
            cell(createDecoratedListPanel())
                .verticalAlign(VerticalAlign.FILL)
                .resizableColumn() // Allow list to grow horizontally

            // Cell for the editor form
            panel {
                row("Name:") {
                    cell(nameField)
                        .align(AlignX.FILL)
                }
                row("Prefix:") {
                    scrollCell(prefixArea) // Make prefix area scrollable
                        .align(Align.FILL) // Take available space
                }.layout(RowLayout.LABEL_ALIGNED).resizableRow() // Align label top, allow vertical resize

                row("Suffix:") {
                    scrollCell(suffixArea) // Make suffix area scrollable
                        .align(Align.FILL) // Take available space
                }.layout(RowLayout.LABEL_ALIGNED).resizableRow() // Align label top, allow vertical resize

            }.verticalAlign(VerticalAlign.TOP) // Align editor panel top
                .resizableColumn() // Allow editor panel to resize horizontally
                .enabled(false) // Initially disabled until a preset is selected

        }.resizableRow() // Allow the main row to resize vertically

        // --- List Selection Listener ---
        presetList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        presetList.addListSelectionListener { e: ListSelectionEvent ->
            if (!e.valueIsAdjusting) {
                val selectedIndex = presetList.selectedIndex
                // Enable/disable editor panel based on selection
                val editorPanel = this.component.components.filterIsInstance<JPanel>().getOrNull(1) // Find editor panel
                editorPanel?.let { enableEditorPanel(it, selectedIndex != -1) }
                loadPresetIntoFields(selectedIndex)
            }
        }
    }

    /**
     * Creates the JList panel wrapped with a ToolbarDecorator for Add/Remove buttons.
     */
    private fun createDecoratedListPanel(): JPanel {
        val decorator = ToolbarDecorator.createDecorator(presetList)
            .setAddAction { addPreset() }
            .setRemoveAction { removePreset() }
            .setMoveUpAction(null) // Disable move actions for simplicity, can be added later
            .setMoveDownAction(null)

        // Custom renderer to display preset names
        presetList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int,
                isSelected: Boolean, cellHasFocus: Boolean
            ): java.awt.Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is Preset) {
                    text = value.name
                }
                return component
            }
        }

        return decorator.createPanel()
    }

    /**
     * Enables or disables all components within the editor panel.
     */
    private fun enableEditorPanel(panel: JPanel, enabled: Boolean) {
        panel.components.forEach { comp ->
            if (comp is JScrollPane) { // Handle scroll panes containing text areas
                comp.viewport.view?.isEnabled = enabled
            }
            comp.isEnabled = enabled
        }
    }


    /**
     * Loads the details of the selected preset into the editor fields.
     * Clears fields if no preset is selected.
     */
    private fun loadPresetIntoFields(selectedIndex: Int) {
        if (selectedIndex != -1 && selectedIndex < listModel.size) {
            val preset = listModel.getElementAt(selectedIndex)
            nameField.text = preset.name
            prefixArea.text = preset.prefix
            suffixArea.text = preset.suffix
        } else {
            nameField.text = ""
            prefixArea.text = ""
            suffixArea.text = ""
        }
    }

    /**
     * Adds a new default preset to the list.
     */
    private fun addPreset() {
        val newPreset = Preset(name = "New Preset ${listModel.size + 1}")
        listModel.add(newPreset)
        presetList.selectedIndex = listModel.size - 1 // Select the new item
        presetList.ensureIndexIsVisible(presetList.selectedIndex) // Scroll to the new item
    }

    /**
     * Removes the currently selected preset from the list.
     */
    private fun removePreset() {
        val selectedIndex = presetList.selectedIndex
        if (selectedIndex != -1) {
            listModel.remove(selectedIndex)
            // Adjust selection if possible
            val newSize = listModel.size
            if (newSize > 0) {
                presetList.selectedIndex = if (selectedIndex >= newSize) newSize - 1 else selectedIndex
            } else {
                // Disable editor panel if list becomes empty
                val editorPanel = panel.component.components.filterIsInstance<JPanel>().getOrNull(1)
                editorPanel?.let { enableEditorPanel(it, false) }
            }
        }
    }

    /**
     * Checks if the current list of presets in the UI differs from the original list loaded.
     * @param original The original list of presets loaded from the service.
     * @return True if modified, false otherwise.
     */
    fun isModified(original: List<Preset>): Boolean {
        val currentPresets = listModel.items
        return original != currentPresets // Simple comparison works if Preset is a data class
    }

    /**
     * Saves the current state of the presets from the UI list to the PresetService.
     * @param service The PresetService instance to save to.
     */
    fun applyTo(service: PresetService) {
        // Update the preset details from the editor fields before saving
        val selectedIndex = presetList.selectedIndex
        if (selectedIndex != -1 && selectedIndex < listModel.size) {
            val preset = listModel.getElementAt(selectedIndex)
            preset.name = nameField.text
            preset.prefix = prefixArea.text
            preset.suffix = suffixArea.text
            // Force list model update to reflect changes if user didn't deselect/reselect
            listModel.contentsChanged(ListDataEvent(listModel, ListDataEvent.CONTENTS_CHANGED, selectedIndex, selectedIndex))
        }

        val currentPresets = listModel.items
        service.setPresets(currentPresets)
        originalPresets = service.getPresets() // Update original state after applying
        println("PresetSettingsPanel: Applied ${currentPresets.size} presets.")
    }

    /**
     * Resets the UI panel to reflect the given list of presets.
     * @param presets The list of presets to load from the service.
     */
    fun resetFrom(presets: List<Preset>) {
        originalPresets = presets
        listModel.replaceAll(presets.map { it.copy() }) // Use copies
        presetList.clearSelection() // Clear selection after reset
        // Disable editor panel after reset
        val editorPanel = panel.component.components.filterIsInstance<JPanel>().getOrNull(1)
        editorPanel?.let { enableEditorPanel(it, false) }
        loadPresetIntoFields(-1) // Clear editor fields
        println("PresetSettingsPanel: Reset to ${presets.size} presets.")
    }
}