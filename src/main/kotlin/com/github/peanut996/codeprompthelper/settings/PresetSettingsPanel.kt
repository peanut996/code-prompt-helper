// src/main/kotlin/com/github/peanut996/codeprompthelper/settings/PresetSettingsPanel.kt
package com.github.peanut996.codeprompthelper.settings

import com.github.peanut996.codeprompthelper.Preset
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane // Keep JBScrollPane import
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.VerticalAlign // Keep explicit import
import java.awt.BorderLayout // Keep BorderLayout import
import java.awt.Container
import java.awt.Component
import javax.swing.*
// No need for ListDataEvent/Listener imports if we use model methods correctly
import javax.swing.event.ListSelectionEvent

class PresetSettingsPanel {

    private val listModel = CollectionListModel<Preset>()
    private val presetList = JBList(listModel)

    private lateinit var nameField: JBTextField
    private lateinit var prefixArea: JBTextArea
    private lateinit var suffixArea: JBTextArea
    private lateinit var editorPanel: JPanel
    private lateinit var removeButton: JButton

    private var originalPresets: List<Preset> = emptyList()
    private var isApplying = false // Flag to prevent listener loops

    val panel: DialogPanel = panel {
        row {
            // --- Left Side: List of Presets ---
            cell(createDecoratedListPanel())
                .verticalAlign(VerticalAlign.FILL) // Apply modifier to the Cell containing the decorated panel
                .resizableColumn() // Apply modifier to the Cell

            // --- Right Side: Editor for Selected Preset ---
            panel {
                row("Name:") {
                    textField() // Create JBTextField via DSL
                        .bindText(::getSelectedPresetName, ::setSelectedPresetName)
                        .align(AlignX.FILL)
                        .comment("Unique name for the preset") // Optional comment
                        .also { // Assign the actual component AFTER configuration
                            nameField = it.component
                        }
                }
                row("Prefix:") {
                    prefixArea = JBTextArea().apply { rows = 5; lineWrap = true; wrapStyleWord = true }
                    scrollCell(prefixArea) // Wrap text area in scroll pane cell
                        .bindText(::getSelectedPresetPrefix, ::setSelectedPresetPrefix)
                        .align(Align.FILL)
                        .comment("Text added before your selected code")
                }.layout(RowLayout.LABEL_ALIGNED).resizableRow()

                row("Suffix:") {
                    suffixArea = JBTextArea().apply { rows = 5; lineWrap = true; wrapStyleWord = true }
                    scrollCell(suffixArea)
                        .bindText(::getSelectedPresetSuffix, ::setSelectedPresetSuffix)
                        .align(Align.FILL)
                        .comment("Text added after your selected code")
                }.layout(RowLayout.LABEL_ALIGNED).resizableRow()

            }.verticalAlign(VerticalAlign.TOP) // Apply modifier to the Cell containing this panel
                .resizableColumn() // Apply modifier to the Cell
                .enabled(false) // Initially disabled
                .also { editorPanel = it.component } // Store reference

        }.resizableRow() // Make the main row resizable vertically

        // --- List Selection Listener ---
        presetList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        presetList.addListSelectionListener { e: ListSelectionEvent ->
            if (!e.valueIsAdjusting && !isApplying) {
                // Save edits from previously selected item *before* loading the new one
                // Note: This might feel slightly unintuitive if the user expects edits
                // to be saved only on 'Apply', but it simplifies state management here.
                // Alternatively, track edits separately and prompt on selection change if unsaved.
                // For now, we save implicitly on selection change.
                saveCurrentSelection() // Save before loading new selection

                val isSelected = presetList.selectedIndex != -1
                enableComponents(editorPanel, isSelected)
                removeButton.isEnabled = isSelected
                loadSelectedPresetIntoFields() // Load new selection's data
            }
        }
    }

    // --- Getter/Setter for Bindings ---
    private fun getSelectedPreset(): Preset? = presetList.selectedValue
    private fun getSelectedPresetName(): String = getSelectedPreset()?.name ?: ""
    private fun getSelectedPresetPrefix(): String = getSelectedPreset()?.prefix ?: ""
    private fun getSelectedPresetSuffix(): String = getSelectedPreset()?.suffix ?: ""

    // Setters now directly modify the model object and notify the list model
    private fun setSelectedPresetName(value: String) {
        getSelectedPreset()?.let { preset ->
            if (preset.name != value) {
                preset.name = value
                // Correct way to notify CollectionListModel about internal item change
                val index = listModel.getElementIndex(preset)
                if (index != -1) {
                    listModel.updateElement(preset) // Use updateElement
                }
            }
        }
    }
    private fun setSelectedPresetPrefix(value: String) { getSelectedPreset()?.prefix = value }
    private fun setSelectedPresetSuffix(value: String) { getSelectedPreset()?.suffix = value }
    // --- End Getter/Setter ---

    private fun createDecoratedListPanel(): JPanel {
        val decorator = ToolbarDecorator.createDecorator(presetList)
            .setAddAction { addPreset() }
            .setRemoveAction { removePreset() }
            .setMoveUpAction(null)
            .setMoveDownAction(null)

        presetList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int,
                isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? Preset)?.name ?: ""
                return this
            }
        }
        val decoratedPanel = decorator.createPanel()
        // Find the remove button more reliably
        removeButton = decorator.actionsPanel?.components?.find {
            it is JButton && it.toolTipText?.contains("Remove", ignoreCase = true) == true
        } as? JButton ?: JButton("Remove") // Fallback
        removeButton.isEnabled = false
        return decoratedPanel
    }

    private fun enableComponents(container: Container, enabled: Boolean) {
        container.isEnabled = enabled
        container.components.forEach { comp ->
            if (comp is JScrollPane) { // Special handling for scroll panes
                comp.viewport?.view?.isEnabled = enabled
                comp.isEnabled = enabled // Also enable/disable the scroll pane itself
            } else if (comp is Container) { // Recurse for other containers
                enableComponents(comp, enabled)
            } else { // Enable/disable individual components
                comp.isEnabled = enabled
            }
        }
    }


    private fun loadSelectedPresetIntoFields() {
        val preset = getSelectedPreset()
        isApplying = true // Prevent listener feedback loops during field updates
        try {
            // Update fields directly, bindings should handle the rest
            nameField.text = preset?.name ?: ""
            prefixArea.text = preset?.prefix ?: ""
            suffixArea.text = preset?.suffix ?: ""
        } finally {
            isApplying = false
        }
    }

    private fun addPreset() {
        saveCurrentSelection() // Save before adding
        val newPreset = Preset(name = "New Preset ${listModel.size + 1}")
        listModel.add(newPreset)
        presetList.selectedIndex = listModel.size - 1
        presetList.ensureIndexIsVisible(presetList.selectedIndex)
    }

    private fun removePreset() {
        val selectedIndex = presetList.selectedIndex
        if (selectedIndex != -1) {
            listModel.remove(selectedIndex)
            val newSize = listModel.size
            if (newSize > 0) {
                presetList.selectedIndex = minOf(selectedIndex, newSize - 1) // Select previous or last
            }
            // Listener will handle field clearing/disabling
        }
    }

    // Saves the currently edited fields back to the selected list item's object
    private fun saveCurrentSelection() {
        val selectedIndex = presetList.selectedIndex
        // Check bounds explicitly
        if (selectedIndex >= 0 && selectedIndex < listModel.size) {
            val preset = listModel.getElementAt(selectedIndex) ?: return // Should not be null, but safe check

            // Check if values actually changed before modifying the object
            val nameChanged = preset.name != nameField.text
            val prefixChanged = preset.prefix != prefixArea.text
            val suffixChanged = preset.suffix != suffixArea.text

            if (nameChanged || prefixChanged || suffixChanged) {
                preset.name = nameField.text
                preset.prefix = prefixArea.text
                preset.suffix = suffixArea.text

                // If the name changed, we need to notify the model to update the list display
                if (nameChanged) {
                    listModel.updateElement(preset) // Use updateElement for CollectionListModel
                }
            }
        }
    }

    fun isModified(original: List<Preset>): Boolean {
        saveCurrentSelection() // Ensure edits are in the model
        val currentPresets = listModel.items
        // Compare content and order
        return original.size != currentPresets.size || original != currentPresets
    }

    fun applyTo(service: PresetService) {
        isApplying = true
        try {
            saveCurrentSelection()
            val currentPresets = listModel.items
            service.setPresets(currentPresets)
            originalPresets = service.getPresets() // Update baseline
        } finally {
            isApplying = false
        }
    }

    fun resetFrom(presets: List<Preset>) {
        isApplying = true
        try {
            originalPresets = presets
            listModel.replaceAll(presets.map { it.copy() }) // Use copies
            presetList.clearSelection()
            enableComponents(editorPanel, false) // Disable editor
            loadSelectedPresetIntoFields() // Clear fields
            removeButton.isEnabled = false // Disable remove button
        } finally {
            isApplying = false
        }
    }
}