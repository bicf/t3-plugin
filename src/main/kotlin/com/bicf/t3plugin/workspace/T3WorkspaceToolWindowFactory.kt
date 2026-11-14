package com.bicf.t3plugin.workspace

import com.bicf.t3plugin.settings.T3Settings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.jcef.JBCefApp

/**
 * The "T3 Workspace" tool window: the right panels of the T3 editor tabs (see [T3WorkspacePanels]).
 * It starts empty; [T3WorkspacePanels] adds a panel when the user asks for one. When the user opens the empty tool
 * window, it shows the panel of the selected T3 tab.
 */
class T3WorkspaceToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                project.service<T3WorkspacePanels>().onContentRemoved(event.content)
            }
        })
        project.messageBus.connect(toolWindow.disposable).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun toolWindowShown(shown: ToolWindow) {
                if (shown.id == ID) project.service<T3WorkspacePanels>().onToolWindowShown()
            }
        })
        ApplicationManager.getApplication().invokeLater({ project.service<T3WorkspacePanels>().onToolWindowShown() }, project.disposed)
    }

    override fun shouldBeAvailable(project: Project): Boolean =
        JBCefApp.isSupported() && T3Settings.getInstance().openThreadsInEditor

    companion object {
        /** Id of the tool window in plugin.xml. */
        const val ID = "T3 Workspace"
    }
}
