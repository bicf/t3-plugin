package com.bicf.t3plugin.editor

import com.bicf.t3plugin.project.T3ProjectWindows
import com.bicf.t3plugin.toolWindow.T3ProjectScope
import com.intellij.openapi.application.ApplicationManager
import com.bicf.t3plugin.toolWindow.T3Routes
import com.bicf.t3plugin.workspace.T3DetailsPanels
import com.bicf.t3plugin.workspace.T3SidePanels
import com.bicf.t3plugin.workspace.T3WorkspacePanels
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * The editor tabs of T3 pages in a project.
 *
 * Rules:
 * - One tab per tab key (see [T3Routes.tabPage]): [open] selects the tab of the key when it is open, otherwise it
 *   opens a new tab.
 * - [lastThreadId] is the thread of the last selected T3 tab, when that tab is still open and shows a thread.
 *   "Ask T3 About Selection…" uses it, and the sidebar of the "T3 Code" tool window marks it (see
 *   [com.bicf.t3plugin.toolWindow.T3SidebarMark]). The selection of a file that is not a T3 page keeps the mark.
 * - The "T3 Workspace" tool window follows the tabs: it selects the panel of the selected tab, closes the panel of a
 *   closed tab, and shows the new page of a draft that became a thread.
 * - A tab that becomes the tab of a thread whose T3 worktree has its own open IDE window closes: that window shows
 *   the thread (see [T3ProjectWindows]).
 * Call the functions on the EDT.
 */
@Service(Service.Level.PROJECT)
class T3PageTabs(private val project: Project) {

    private var last: T3PageFile? = null

    /**
     * Opens or selects the tab of [path]. [focus] moves the keyboard focus to the tab.
     * Returns null when [path] is not a tab page.
     */
    fun open(path: String, focus: Boolean = true): T3PageFile? {
        val page = T3Routes.tabPage(path) ?: return null
        val file = find(page.key) ?: T3PageFile(path)
        // `FileEditorManager.openFile(VirtualFile, Boolean)` is deprecated in 2026.3.
        OpenFileDescriptor(project, file).navigate(focus)
        select(file)
        return file
    }

    fun find(key: String): T3PageFile? = openPageFiles().firstOrNull { it.page.key == key }

    fun hasOpenTabs(): Boolean = openPageFiles().isNotEmpty()

    fun lastThreadId(): String? = lastThreadRoute()?.threadId

    /** The thread of the last selected T3 tab, when that tab is open and is not [closing]. */
    fun lastThreadRoute(closing: VirtualFile? = null): T3Routes.ThreadRoute? =
        last?.takeIf { it != closing && it in openPageFiles() }?.threadRoute

    private fun select(file: T3PageFile) {
        last = file
        updateSidebarMark()
    }

    /**
     * Marks the thread of [lastThreadRoute] in the sidebar of the "T3 Code" tool window, when the tool window is open.
     * [closing] is a tab that closes now: the IDE can still list it in the open files.
     */
    private fun updateSidebarMark(closing: VirtualFile? = null) {
        project.serviceIfCreated<T3ProjectScope>()?.browserPanel?.markThread(lastThreadRoute(closing))
    }

    /** Shows [title] on the tab of [file] and on its panel. The tab title is [T3PageFile.getPresentableName]. */
    fun updateTitle(file: T3PageFile, title: String) {
        if (file.title == title) return
        file.title = title
        FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(file)
        sidePanels().forEach { it.onTitleChanged(file) }
    }

    /**
     * The page of [file] is now [path]: a page of the same key, the thread that a draft became, or the page of a redirect.
     * The panel of the tab shows the new page.
     */
    fun changePath(file: T3PageFile, path: String) {
        if (file.pagePath == path) return
        val keyChanged = T3Routes.tabPage(path)?.key != file.page.key
        file.pagePath = path
        if (keyChanged) {
            updateTitle(file, T3PageFile.defaultTitle(path))
            sidePanels().forEach { it.onPathChanged(file) }
            if (file == last) updateSidebarMark()
            // The window of the T3 worktree of this thread shows the thread already: this tab closes.
            val threadId = file.threadRoute?.threadId
            if (threadId != null && serviceIfCreated<T3ProjectWindows>()?.inOtherWorktreeWindow(project, threadId) == true) {
                // The page of the tab reported this path: the tab closes after the page message.
                ApplicationManager.getApplication().invokeLater({ close(file) }, project.disposed)
            }
        }
    }

    fun close(file: T3PageFile) = FileEditorManager.getInstance(project).closeFile(file)

    /** The tool windows that follow the T3 tabs: "T3 Workspace" and "T3 Details". */
    private fun sidePanels(): List<T3SidePanels> =
        listOf(project.service<T3WorkspacePanels>(), project.service<T3DetailsPanels>())

    private fun openPageFiles(): List<T3PageFile> =
        FileEditorManager.getInstance(project).openFiles.filterIsInstance<T3PageFile>()

    /** Remembers the selected T3 tab and keeps the "T3 Workspace" and "T3 Details" tool windows in step. Registered in plugin.xml. */
    internal class SelectionListener(private val project: Project) : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
            val file = event.newFile as? T3PageFile ?: return
            project.service<T3PageTabs>().select(file)
            project.service<T3PageTabs>().sidePanels().forEach { it.onTabSelected(file) }
        }

        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
            if (file !is T3PageFile) return
            project.service<T3PageTabs>().sidePanels().forEach { it.onTabClosed(file) }
            project.service<T3PageTabs>().updateSidebarMark(closing = file)
        }
    }
}
