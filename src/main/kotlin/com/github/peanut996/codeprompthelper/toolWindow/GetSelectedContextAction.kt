package com.github.peanut996.codeprompthelper.toolWindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.diagnostic.logger

class GetSelectedContextAction : AnAction() {

    private val LOG = logger<GetSelectedContextAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CodePromptHelperToolWindow") ?: return

        val contentManager = toolWindow.contentManager
        val content = contentManager.contents.firstOrNull() ?: return
        val component = content.component

        val promptToolWindow = (component as? PromptHelperToolWindowPanel)?.toolWindowImpl
            ?: component.getClientProperty(PromptHelperToolWindow::class.java.name) as? PromptHelperToolWindow

        if (promptToolWindow != null) {
            promptToolWindow.copySelectedContextToClipboard()
        } else {
            LOG.warn("PromptHelperToolWindow not found. Cannot copy selected context.")
        }
    }
}
