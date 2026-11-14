package com.bicf.t3plugin.workspace

import com.bicf.t3plugin.editor.T3PageFile
import com.bicf.t3plugin.toolWindow.T3BrowserPanel
import com.bicf.t3plugin.toolWindow.T3PageTarget
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory

/**
 * The right panels of T3 pages ("Workspace panel" of T3: browser, device, pull requests, side chat, …) in the
 * "T3 Workspace" tool window. Each editor tab has at most one panel; the panel shows the right panel of the page of
 * the tab (see [com.bicf.t3plugin.toolWindow.T3Layout.Mode.PANEL]).
 *
 * Rules:
 * - The plugin creates a panel only when the user asks for it: "Toggle right panel" in the tab, or T3 opens its right
 *   panel in the tab.
 * - [toggle] hides the tool window when it shows the panel of the tab; otherwise it shows that panel.
 * - [show] with `reload` loads the page again, so the panel reads the surfaces that the tab added (T3 keeps them in
 *   `localStorage`).
 * - The panel follows its tab: selection, title, new page (a draft that became a thread), and close.
 * Call the functions on the EDT.
 */
@Service(Service.Level.PROJECT)
class T3WorkspacePanels(private val project: Project) {

    private class Entry(val content: Content, val panel: T3BrowserPanel)

    private val panels = LinkedHashMap<T3PageFile, Entry>()

    fun toggle(file: T3PageFile) {
        val toolWindow = toolWindow() ?: return
        val entry = panels[file]
        if (toolWindow.isVisible && entry != null && toolWindow.contentManager.selectedContent === entry.content) {
            toolWindow.hide()
        } else {
            show(file, reload = false)
        }
    }

    fun show(file: T3PageFile, reload: Boolean) {
        val toolWindow = toolWindow() ?: return
        toolWindow.isAvailable = true
        val existing = panels[file]
        val entry = existing ?: create(toolWindow, file)
        toolWindow.contentManager.setSelectedContent(entry.content)
        toolWindow.show()
        if (existing != null && reload) existing.panel.reload()
    }

    /**
     * The user opened the tool window. Without a panel, the tool window shows the panel of the selected T3 tab.
     * A hidden tool window stays hidden: the platform also creates the content when code reads the content manager.
     * Called by [T3WorkspaceToolWindowFactory].
     */
    internal fun onToolWindowShown() {
        val toolWindow = toolWindow() ?: return
        if (!toolWindow.isVisible || toolWindow.contentManager.contentCount > 0) return
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() as? T3PageFile ?: return
        show(file, reload = false)
    }

    /** Selects the panel of [file]. A visible tool window without panels shows the panel of [file]. */
    fun onTabSelected(file: T3PageFile) {
        val toolWindow = toolWindow() ?: return
        val entry = panels[file]
        when {
            entry != null -> toolWindow.contentManager.setSelectedContent(entry.content)
            toolWindow.isVisible && toolWindow.contentManager.contentCount == 0 -> show(file, reload = false)
        }
    }

    fun onTabClosed(file: T3PageFile) {
        val entry = panels.remove(file) ?: return
        val toolWindow = toolWindow() ?: return
        toolWindow.contentManager.removeContent(entry.content, true)
        if (toolWindow.contentManager.contentCount == 0) toolWindow.hide()
    }

    fun onPathChanged(file: T3PageFile) {
        panels[file]?.panel?.connect()
    }

    fun onTitleChanged(file: T3PageFile) {
        panels[file]?.content?.displayName = file.title
    }

    /** The user closed a panel in the tool window. Called by [T3WorkspaceToolWindowFactory]. */
    internal fun onContentRemoved(content: Content) {
        val file = content.getUserData(FILE) ?: return
        if (panels[file]?.content === content) panels.remove(file)
    }

    private fun create(toolWindow: ToolWindow, file: T3PageFile): Entry {
        val panel = T3BrowserPanel(project, T3PageTarget.Panel(file))
        val content = ContentFactory.getInstance().createContent(panel.component, file.title, false).apply {
            setDisposer(panel)
            isCloseable = true
            putUserData(FILE, file)
        }
        toolWindow.contentManager.addContent(content)
        return Entry(content, panel).also { panels[file] = it }
    }

    private fun toolWindow(): ToolWindow? =
        ToolWindowManager.getInstance(project).getToolWindow(T3WorkspaceToolWindowFactory.ID)

    private companion object {
        val FILE = Key.create<T3PageFile>("t3.workspace.file")
    }
}
