package com.bicf.t3plugin.editor

import com.bicf.t3plugin.toolWindow.T3Routes
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project

/**
 * The editor tabs of T3 threads in a project.
 *
 * Rules:
 * - One tab per thread: [open] selects the tab of the thread when it is open, otherwise it opens a new tab.
 * - [lastThreadId] is the thread of the last selected T3 tab that is still open. "Ask T3 About Selection…" uses it.
 * Call the functions on the EDT.
 */
@Service(Service.Level.PROJECT)
class T3ThreadTabs(private val project: Project) {

    private var last: T3ThreadFile? = null

    /** Opens or selects the tab of [route]. [focus] moves the keyboard focus to the tab. */
    fun open(route: T3Routes.ThreadRoute, title: String? = null, focus: Boolean = true): T3ThreadFile {
        val file = find(route) ?: T3ThreadFile(route, title)
        // `FileEditorManager.openFile(VirtualFile, Boolean)` is deprecated in 2026.3.
        OpenFileDescriptor(project, file).navigate(focus)
        last = file
        return file
    }

    fun find(route: T3Routes.ThreadRoute): T3ThreadFile? =
        openThreadFiles().firstOrNull { it.route == route }

    fun hasOpenTabs(): Boolean = openThreadFiles().isNotEmpty()

    fun lastThreadId(): String? = last?.takeIf { it in openThreadFiles() }?.route?.threadId

    /** Shows [title] on the tab of [file]. The tab title is [T3ThreadFile.getPresentableName]. */
    fun updateTitle(file: T3ThreadFile, title: String) {
        if (file.title == title) return
        file.title = title
        FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(file)
    }

    private fun openThreadFiles(): List<T3ThreadFile> =
        FileEditorManager.getInstance(project).openFiles.filterIsInstance<T3ThreadFile>()

    /** Remembers the selected T3 tab. Registered in plugin.xml. */
    internal class SelectionListener(private val project: Project) : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
            val file = event.newFile as? T3ThreadFile ?: return
            project.service<T3ThreadTabs>().last = file
        }
    }
}
