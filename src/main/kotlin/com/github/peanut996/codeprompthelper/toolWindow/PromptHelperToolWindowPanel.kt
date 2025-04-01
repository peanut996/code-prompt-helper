package com.github.peanut996.codeprompthelper.toolWindow

import java.awt.BorderLayout
import javax.swing.JPanel

class PromptHelperToolWindowPanel(
    val toolWindowImpl: PromptHelperToolWindow
) : JPanel(BorderLayout()) {
    init {
        add(toolWindowImpl.contentPanel, BorderLayout.CENTER)
    }
}
