// src/main/kotlin/com/github/peanut996/codeprompthelper/ApplyPresetAction.kt
package com.github.peanut996.codeprompthelper

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
// import com.intellij.ui.awt.RelativePoint // 可能不需要，取决于 showInBestPositionFor 的具体用法
import com.github.peanut996.codeprompthelper.settings.PresetService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import java.awt.datatransfer.StringSelection
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

/**
 * Action triggered from the editor context menu or via shortcut.
 * Allows the user to select a preset and applies it to the selected text,
 * copying the result to the clipboard.
 */
class ApplyPresetAction : AnAction() {

    /**
     * Executes the action logic.
     * @param e The action event context.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val editor: Editor? = e.getData(CommonDataKeys.EDITOR)
        val project: Project? = e.project // Get project context

        // Ensure editor and project are available
        if (editor == null || project == null) {
            // Log or show subtle error if needed
            return
        }

        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText

        // Ensure text is selected
        if (selectedText.isNullOrBlank()) {
            // Optionally show a balloon notification or do nothing silently
            return
        }

        // Load presets using the service
        val presetService = PresetService.getInstance()
        val presets = presetService.getPresets()

        // Handle case where no presets are configured
        if (presets.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No prompt presets configured. Please add presets in Settings > Tools > Code Prompt Helper Presets.",
                "No Presets Found"
            )
            return
        }

        // --- Show Preset Selection Popup ---
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(presets) // Directly use the list of Preset objects
            .setTitle("Select Prompt Preset")
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(true)
            .setRenderer(object : DefaultListCellRenderer() { // Custom renderer to show preset name
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): java.awt.Component {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (value is Preset) {
                        text = value.name // Display the name property
                    }
                    return this // Return the configured component
                }
            })
            .setItemChosenCallback { selectedPreset -> // Callback when an item is chosen
                // Ensure a preset was actually selected
                if (selectedPreset != null) {
                    applyPresetAndCopyToClipboard(project, selectedPreset, selectedText)
                }
            }
            .createPopup()
            .showInBestPositionFor(e.dataContext) // Show popup relative to the action context
    }

    /**
     * Combines the preset with the selected text and copies the result to the clipboard.
     * @param project The current project.
     * @param preset The selected Preset object.
     * @param selectedText The text selected in the editor.
     */
    private fun applyPresetAndCopyToClipboard(project: Project, preset: Preset, selectedText: String) {
        // Construct the final text using prefix and suffix from the preset
        val finalText = "${preset.prefix}${selectedText}${preset.suffix}"

        // Use ApplicationManager for clipboard access on the correct thread
        ApplicationManager.getApplication().invokeLater {
            try {
                CopyPasteManager.getInstance().setContents(StringSelection(finalText))
                // Optional: Show a confirmation balloon notification
                val notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup("CodePromptHelper.Success") // Define this group in plugin.xml if needed
                    .createNotification("Prompt '${preset.name}' copied!", NotificationType.INFORMATION)
                Notifications.Bus.notify(notification, project)
            } catch (ex: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Failed to copy text to clipboard: ${ex.message}",
                    "Clipboard Error"
                )
            }
        }
    }

    /**
     * Updates the visibility and enabled state of the action based on the context.
     * The action is only enabled if an editor is active and text is selected.
     * @param e The action event context.
     */
    override fun update(e: AnActionEvent) {
        val editor: Editor? = e.getData(CommonDataKeys.EDITOR)
        val presentation: Presentation = e.presentation

        // Enable the action only if an editor exists and has a non-empty selection
        presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() == true
    }

    /**
     * Specifies the thread on which the `update` method should run.
     * BGT (Background Thread) is generally preferred for performance.
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}