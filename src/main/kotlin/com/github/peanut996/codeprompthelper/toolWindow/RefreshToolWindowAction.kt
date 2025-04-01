package com.github.peanut996.codeprompthelper.toolWindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

class RefreshToolWindowAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CodePromptHelperToolWindow") ?: return

        if (toolWindow.isVisible) {
            PromptHelperToolWindowFactory.refresh(project, toolWindow)
        }
    }
}
