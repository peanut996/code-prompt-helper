// src/main/kotlin/com/github/peanut996/codeprompthelper/settings/PresetSettingsPanel.kt
package com.github.peanut996.codeprompthelper.settings

import com.github.peanut996.codeprompthelper.Preset
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane // 确保导入
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.VerticalAlign // 显式导入 VerticalAlign
import com.intellij.ui.dsl.gridLayout.HorizontalAlign // 如果需要 AlignX，则导入 HorizontalAlign
import java.awt.BorderLayout // 确保导入
import java.awt.Container
import java.awt.Component
import javax.swing.*
import javax.swing.event.ListSelectionEvent

class PresetSettingsPanel {

    private val listModel = CollectionListModel<Preset>()
    private val presetList = JBList(listModel)

    // lateinit vars for UI components
    private lateinit var nameField: JBTextField
    private lateinit var prefixArea: JBTextArea
    private lateinit var suffixArea: JBTextArea
    // editorPanel 将是包含编辑字段的 JPanel
    private lateinit var editorPanel: JPanel
    private lateinit var removeButton: JButton

    private var originalPresets: List<Preset> = emptyList()
    private var isApplying = false // Flag to prevent listener loops

    // --- Getter/Setter for Bindings ---
    private fun getSelectedPreset(): Preset? = presetList.selectedValue
    private fun getSelectedPresetName(): String = getSelectedPreset()?.name ?: ""
    private fun getSelectedPresetPrefix(): String = getSelectedPreset()?.prefix ?: ""
    private fun getSelectedPresetSuffix(): String = getSelectedPreset()?.suffix ?: ""

    private fun setSelectedPresetName(value: String) {
        getSelectedPreset()?.let { preset ->
            if (preset.name != value) {
                preset.name = value
                val index = listModel.getElementIndex(preset)
                if (index != -1) {
                    listModel.setElementAt(preset, index) // Use setElementAt for update notification
                }
            }
        }
    }
    private fun setSelectedPresetPrefix(value: String) { getSelectedPreset()?.prefix = value }
    private fun setSelectedPresetSuffix(value: String) { getSelectedPreset()?.suffix = value }
    // --- End Getter/Setter ---

    // Build the main panel using Kotlin UI DSL
    val panel: DialogPanel = panel { // 最外层是 DialogPanel
        row {
            // --- Left Side: List of Presets ---
            cell(createDecoratedListPanel())
                .verticalAlign(VerticalAlign.FILL)
                .resizableColumn()

            // --- Right Side: Editor for Selected Preset ---
            // 1. 创建内部 panel (它返回 JPanel) 并将其赋给 editorPanel
            editorPanel = panel {
                row("Name:") {
                    // 创建 textField 并使用 .also 获取引用
                    textField()
                        .bindText(::getSelectedPresetName, ::setSelectedPresetName)
                        .align(AlignX.FILL) // 使用 AlignX
                        .comment("Unique name for the preset")
                        .also { nameField = it.component } // 获取 JBTextField 实例
                }
                row("Prefix:") {
                    prefixArea = JBTextArea().apply { rows = 5; lineWrap = true; wrapStyleWord = true }
                    scrollCell(prefixArea) // 将组件放入 scrollCell
                        .bindText(::getSelectedPresetPrefix, ::setSelectedPresetPrefix)
                        .align(Align.FILL) // 使用 Align
                        .comment("Text added before your selected code")
                }.layout(RowLayout.LABEL_ALIGNED).resizableRow()

                row("Suffix:") {
                    suffixArea = JBTextArea().apply { rows = 5; lineWrap = true; wrapStyleWord = true }
                    scrollCell(suffixArea)
                        .bindText(::getSelectedPresetSuffix, ::setSelectedPresetSuffix)
                        .align(Align.FILL) // 使用 Align
                        .comment("Text added after your selected code")
                }.layout(RowLayout.LABEL_ALIGNED).resizableRow()
            } // 内部 panel 定义结束

            // 2. 将创建好的 editorPanel (JPanel) 添加到外部 row 的 cell 中
            //    并对这个 cell 应用修饰符
            cell(editorPanel) // *** 将 JPanel 添加到 cell ***
                .verticalAlign(VerticalAlign.TOP)
                .resizableColumn()
                .enabled(false) // 控制 Cell (及其内容) 的初始启用状态

        }.resizableRow()

        // --- List Selection Listener ---
        presetList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        presetList.addListSelectionListener { e: ListSelectionEvent ->
            if (!e.valueIsAdjusting && !isApplying) {
                saveCurrentSelection() // Save previous

                val isSelected = presetList.selectedIndex != -1
                // 直接启用/禁用 editorPanel (JPanel) 及其内容
                enableComponents(editorPanel, isSelected)
                // 确保 removeButton 已初始化
                if (::removeButton.isInitialized) {
                    removeButton.isEnabled = isSelected
                }
                loadSelectedPresetIntoFields() // Load new
            }
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
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? Preset)?.name ?: ""
                return this
            }
        }
        val decoratedPanel = decorator.createPanel()
        // 查找按钮 - 在面板创建后进行
        removeButton = decoratedPanel.components.filterIsInstance<JPanel>().firstOrNull() // ToolbarDecorator usually wraps actions in a panel
            ?.components?.find {
                it is JButton && (it.icon?.toString()?.contains("remove") == true || it.toolTipText?.contains("Remove", ignoreCase = true) == true)
            } as? JButton ?: JButton("Remove") // 更健壮的回退
        removeButton.isEnabled = false
        return decoratedPanel
    }

    // 保持不变
    private fun enableComponents(container: Container, enabled: Boolean) {
        container.isEnabled = enabled
        container.components.forEach { comp ->
            if (comp is JScrollPane) {
                comp.viewport?.view?.isEnabled = enabled
                comp.isEnabled = enabled
            } else if (comp is Container) {
                enableComponents(comp, enabled)
            } else {
                comp.isEnabled = enabled
            }
        }
    }

    // 保持不变
    private fun loadSelectedPresetIntoFields() {
        val preset = getSelectedPreset()
        isApplying = true
        try {
            // 确保 lateinit var 已初始化
            if (::nameField.isInitialized) nameField.text = preset?.name ?: ""
            if (::prefixArea.isInitialized) prefixArea.text = preset?.prefix ?: ""
            if (::suffixArea.isInitialized) suffixArea.text = preset?.suffix ?: ""
        } finally {
            isApplying = false
        }
    }

    // 保持不变
    private fun addPreset() {
        saveCurrentSelection()
        val newPreset = Preset(name = "New Preset ${listModel.size + 1}")
        listModel.add(newPreset)
        presetList.selectedIndex = listModel.size - 1
        presetList.ensureIndexIsVisible(presetList.selectedIndex)
    }

    // 保持不变
    private fun removePreset() {
        val selectedIndex = presetList.selectedIndex
        if (selectedIndex != -1) {
            listModel.remove(selectedIndex)
            val newSize = listModel.size
            if (newSize > 0) {
                presetList.selectedIndex = minOf(selectedIndex, newSize - 1)
            } else {
                if (::editorPanel.isInitialized) enableComponents(editorPanel, false)
                loadSelectedPresetIntoFields()
                if (::removeButton.isInitialized) removeButton.isEnabled = false
            }
        }
    }

    // 保持不变
    private fun saveCurrentSelection() {
        val selectedIndex = presetList.selectedIndex
        if (selectedIndex >= 0 && selectedIndex < listModel.size) {
            val preset = listModel.getElementAt(selectedIndex) ?: return

            // 检查 lateinit var 是否已初始化
            if (::nameField.isInitialized && ::prefixArea.isInitialized && ::suffixArea.isInitialized) {
                val nameChanged = preset.name != nameField.text
                val prefixChanged = preset.prefix != prefixArea.text
                val suffixChanged = preset.suffix != suffixArea.text

                if (nameChanged || prefixChanged || suffixChanged) {
                    preset.name = nameField.text
                    preset.prefix = prefixArea.text
                    preset.suffix = suffixArea.text

                    if (nameChanged) {
                        listModel.setElementAt(preset, selectedIndex) // Use setElementAt
                    }
                }
            }
        }
    }

    // 保持不变
    fun isModified(original: List<Preset>): Boolean {
        saveCurrentSelection()
        val currentPresets = listModel.items
        return original.size != currentPresets.size || original != currentPresets
    }

    // 保持不变
    fun applyTo(service: PresetService) {
        isApplying = true
        try {
            saveCurrentSelection()
            val currentPresets = listModel.items
            service.setPresets(currentPresets)
            originalPresets = service.getPresets()
        } finally {
            isApplying = false
        }
    }

    // 保持不变
    fun resetFrom(presets: List<Preset>) {
        isApplying = true
        try {
            originalPresets = presets
            listModel.replaceAll(presets.map { it.copy() })
            presetList.clearSelection()
            if(::editorPanel.isInitialized) {
                enableComponents(editorPanel, false)
            }
            loadSelectedPresetIntoFields()
            if(::removeButton.isInitialized) {
                removeButton.isEnabled = false
            }
        } finally {
            isApplying = false
        }
    }
}