package com.github.peanut996.codeprompthelper.settings

import com.github.peanut996.codeprompthelper.Preset
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.RowLayout
import java.awt.Component
import java.awt.Container
import javax.swing.*
import javax.swing.event.ListSelectionEvent

class PresetSettingsPanel {

    private val listModel = CollectionListModel<Preset>()
    private val presetList = JBList(listModel)

    private val nameField = JBTextField()
    private val prefixArea = JBTextArea().apply { lineWrap = true; wrapStyleWord = true }
    private val suffixArea = JBTextArea().apply { lineWrap = true; wrapStyleWord = true }

    private var originalPresets: List<Preset> = emptyList()

    private lateinit var editorPanel: JPanel

    val panel: DialogPanel = panel {
        row {
            cell(createDecoratedListPanel())
                .align(Align.FILL)

            editorPanel = createEditorPanel()
            cell(editorPanel).align(Align.FILL)
        }

        presetList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        presetList.addListSelectionListener { e: ListSelectionEvent ->
            if (!e.valueIsAdjusting) {
                val selectedIndex = presetList.selectedIndex
                editorPanel.isEnabled = selectedIndex != -1
                updateEditorEnabledState(editorPanel, selectedIndex != -1)
                loadPresetIntoFields(selectedIndex)
            }
        }
    }

    private fun createEditorPanel(): JPanel {
        val formPanel = panel {
            row("Name:") {
                cell(nameField).align(AlignX.FILL)
            }
            row("Prefix:") {
                scrollCell(prefixArea).align(Align.FILL)
            }.resizableRow().layout(RowLayout.LABEL_ALIGNED)
            row("Suffix:") {
                scrollCell(suffixArea).align(Align.FILL)
            }.resizableRow().layout(RowLayout.LABEL_ALIGNED)
        }
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(formPanel)
            isEnabled = false
        }
    }

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
                val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? Preset)?.name ?: ""
                return comp
            }
        }

        return decorator.createPanel()
    }

    private fun loadPresetIntoFields(index: Int) {
        if (index != -1 && index < listModel.size) {
            val preset = listModel.getElementAt(index)
            nameField.text = preset.name
            prefixArea.text = preset.prefix
            suffixArea.text = preset.suffix
        } else {
            nameField.text = ""
            prefixArea.text = ""
            suffixArea.text = ""
        }
    }

    private fun addPreset() {
        val newPreset = Preset(name = "New Preset ${listModel.size + 1}")
        listModel.add(newPreset)
        presetList.selectedIndex = listModel.size - 1
        presetList.ensureIndexIsVisible(presetList.selectedIndex)
    }

    private fun removePreset() {
        val index = presetList.selectedIndex
        if (index != -1) {
            listModel.remove(index)
            val newSize = listModel.size
            presetList.selectedIndex = if (index >= newSize) newSize - 1 else index
        }
    }

    fun isModified(original: List<Preset>): Boolean {
        return original != listModel.items
    }

    fun applyTo(service: PresetService) {
        val index = presetList.selectedIndex
        if (index != -1 && index < listModel.size) {
            val preset = listModel.getElementAt(index)
            preset.name = nameField.text
            preset.prefix = prefixArea.text
            preset.suffix = suffixArea.text
        }
        val currentPresets = listModel.items
        service.setPresets(currentPresets)
        originalPresets = service.getPresets()
    }

    fun resetFrom(presets: List<Preset>) {
        originalPresets = presets
        listModel.replaceAll(presets.map { it.copy() })
        presetList.clearSelection()
        loadPresetIntoFields(-1)
        updateEditorEnabledState(editorPanel, false)
    }

    private fun updateEditorEnabledState(container: Container, enabled: Boolean) {
        container.isEnabled = enabled
        container.components.forEach { comp ->
            comp.isEnabled = enabled
            if (comp is Container) {
                updateEditorEnabledState(comp, enabled)
            }
        }
    }
}
