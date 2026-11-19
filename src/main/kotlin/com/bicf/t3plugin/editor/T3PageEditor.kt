package com.bicf.t3plugin.editor

import com.bicf.t3plugin.toolWindow.T3BrowserPanel
import com.bicf.t3plugin.toolWindow.T3PageTarget
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/** One T3 page in an editor tab. The tab title follows the thread title of the page. */
internal class T3PageEditor(project: Project, private val file: T3PageFile) : UserDataHolderBase(), FileEditor {

    private val panel = T3BrowserPanel(project, T3PageTarget.Tab(file)) { title ->
        project.service<T3PageTabs>().updateTitle(file, title)
    }

    init {
        Disposer.register(this, panel)
        file.addThreadMenu(panel.threadMenu)
    }

    override fun getComponent(): JComponent = panel.component
    override fun getPreferredFocusedComponent(): JComponent = panel.focusComponent
    override fun getName(): String = "T3"
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) = Unit
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun dispose() {
        file.removeThreadMenu(panel.threadMenu)
    }
}

/** Opens a [T3PageFile] in a [T3PageEditor]. Registered in plugin.xml. */
internal class T3PageEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = file is T3PageFile
    override fun acceptRequiresReadAction(): Boolean = false
    override fun createEditor(project: Project, file: VirtualFile): FileEditor = T3PageEditor(project, file as T3PageFile)
    override fun getEditorTypeId(): String = "t3-thread"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
