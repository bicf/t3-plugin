package com.bicf.t3plugin.toolWindow

import com.bicf.t3plugin.T3Bundle
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Phase 1 scaffold: an empty "T3 Code" tool window.
 * Phase 2 replaces the placeholder with a JBCefBrowser that loads the paired T3 Code web UI.
 */
class T3ToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val message = if (JBCefApp.isSupported()) {
            T3Bundle["toolWindow.placeholder"]
        } else {
            T3Bundle["toolWindow.jcefUnsupported"]
        }
        val panel = JPanel(BorderLayout()).apply {
            add(JBLabel(message, SwingConstants.CENTER), BorderLayout.CENTER)
        }
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }
}
