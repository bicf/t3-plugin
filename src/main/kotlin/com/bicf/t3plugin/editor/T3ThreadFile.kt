package com.bicf.t3plugin.editor

import com.bicf.t3plugin.T3Bundle
import com.bicf.t3plugin.toolWindow.T3Routes
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import javax.swing.Icon

/**
 * A T3 thread in an editor tab. The file exists only in memory: the IDE does not reopen it after a restart.
 * [title] is the thread title that the page shows; the tab shows it through [getPresentableName].
 */
class T3ThreadFile(val route: T3Routes.ThreadRoute, title: String?) :
    LightVirtualFile("T3 ${route.threadId}", T3ThreadFileType, "") {

    @Volatile
    var title: String = title?.takeIf { it.isNotBlank() } ?: T3Bundle["editor.untitled"]

    init {
        isWritable = false
    }

    override fun getPresentableName(): String = title
}

/** File type of [T3ThreadFile]. The plugin does not register it: no file on disk has this type. */
object T3ThreadFileType : FileType {
    private val ICON: Icon = IconLoader.getIcon("/icons/toolWindow.svg", T3ThreadFileType::class.java)

    override fun getName(): String = "T3 Thread"
    override fun getDescription(): String = T3Bundle["editor.fileType"]
    override fun getDefaultExtension(): String = ""
    override fun getIcon(): Icon = ICON
    override fun isBinary(): Boolean = true
    override fun isReadOnly(): Boolean = true
    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
}
