package com.bicf.t3plugin.workspace

import com.bicf.t3plugin.settings.T3Settings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.jcef.JBCefApp

/**
 * A tool window of [T3SidePanels]. It starts empty; the service adds a panel when the user asks for one. When the user
 * opens the empty tool window, it shows the panel of the selected T3 tab.
 */
abstract class T3SidePanelsToolWindowFactory(private val id: String) : ToolWindowFactory, DumbAware {

    protected abstract fun panels(project: Project): T3SidePanels

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                panels(project).onContentRemoved(event.content)
            }
        })
        project.messageBus.connect(toolWindow.disposable).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun toolWindowShown(shown: ToolWindow) {
                if (shown.id == id) panels(project).onToolWindowShown()
            }
        })
        ApplicationManager.getApplication().invokeLater({ panels(project).onToolWindowShown() }, project.disposed)
    }

    override fun shouldBeAvailable(project: Project): Boolean =
        JBCefApp.isSupported() && T3Settings.getInstance().openThreadsInEditor
}

/** The "T3 Workspace" tool window: the right panels of the T3 editor tabs (see [T3WorkspacePanels]). */
class T3WorkspaceToolWindowFactory : T3SidePanelsToolWindowFactory(ID) {

    override fun panels(project: Project): T3SidePanels = project.getService(T3WorkspacePanels::class.java)

    companion object {
        /** Id of the tool window in plugin.xml. */
        const val ID = "T3 Workspace"
    }
}

/** The "T3 Details" tool window: the "Thread details" cards of the T3 editor tabs (see [T3DetailsPanels]). */
class T3DetailsToolWindowFactory : T3SidePanelsToolWindowFactory(ID) {

    override fun panels(project: Project): T3SidePanels = project.getService(T3DetailsPanels::class.java)

    companion object {
        /** Id of the tool window in plugin.xml. */
        const val ID = "T3 Details"
    }
}
