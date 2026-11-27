package com.bicf.t3plugin.toolWindow

import com.bicf.t3plugin.T3Bundle
import com.bicf.t3plugin.actions.T3NewWorktreeThreadAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp

/**
 * The "T3 Code" tool window.
 * With JCEF it shows the T3 Code web UI; without JCEF it offers to open T3 Code in the system browser.
 */
class T3ToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val factory = ContentFactory.getInstance()
        val content = if (JBCefApp.isSupported()) {
            val panel = T3BrowserPanel(project)
            // "New T3 Thread in This Worktree" shows only in the window of a Git worktree.
            val newWorktreeThread = ActionManager.getInstance().getAction(T3NewWorktreeThreadAction.ID)
            toolWindow.setTitleActions(listOfNotNull(newWorktreeThread, ReconnectAction(panel)))
            factory.createContent(panel.component, null, false).apply { setDisposer(panel) }
        } else {
            factory.createContent(T3FallbackPanel(project).component, null, false)
        }
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        /** Id of the tool window in plugin.xml. */
        const val ID = "T3 Code"
    }

    private class ReconnectAction(private val panel: T3BrowserPanel) :
        DumbAwareAction(T3Bundle.messagePointer("action.reconnect.text"), AllIcons.Actions.Refresh) {

        override fun actionPerformed(e: AnActionEvent) = panel.connect()

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }
}
