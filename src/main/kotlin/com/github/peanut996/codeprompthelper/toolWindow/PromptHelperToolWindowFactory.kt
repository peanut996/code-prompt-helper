package com.github.peanut996.codeprompthelper.toolWindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.Content

/**
 * 工厂类，负责创建 Tool Window 的内容。
 * 实现 DumbAware 允许 Tool Window 在索引期间也可用。
 */
class PromptHelperToolWindowFactory : ToolWindowFactory, DumbAware {

    /**
     * 当 Tool Window 被打开时，IDE 会调用此方法来创建其内容。
     * @param project 当前项目。
     * @param toolWindow Tool Window 实例。
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val impl = PromptHelperToolWindow(project)
        val wrapper = PromptHelperToolWindowPanel(impl)
        val content = ContentFactory.getInstance().createContent(wrapper, null, false)
        toolWindow.contentManager.addContent(content)
    }

    /**
     * 决定 Tool Window 是否应该在项目启动时可用。
     * 返回 true 使其总是可用。
     */
    override fun shouldBeAvailable(project: Project) = true


    companion object {
        fun refresh(project: Project, toolWindow: ToolWindow) {
            val contentManager = toolWindow.contentManager
            contentManager.removeAllContents(true)

            val impl = PromptHelperToolWindow(project)
            val wrapper = PromptHelperToolWindowPanel(impl)
            val content = ContentFactory.getInstance().createContent(wrapper, null, false)
            contentManager.addContent(content)
        }
    }
}