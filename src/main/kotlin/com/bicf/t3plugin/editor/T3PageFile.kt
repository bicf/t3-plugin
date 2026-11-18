package com.bicf.t3plugin.editor

import com.bicf.t3plugin.T3Bundle
import com.bicf.t3plugin.toolWindow.T3Routes
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import javax.swing.Icon

/**
 * A T3 page in an editor tab: a thread, a draft or an app page such as the settings (see [T3Routes.tabPage]).
 * The file exists only in memory: the IDE does not reopen it after a restart.
 *
 * Rules:
 * - [pagePath] is a tab page. It changes inside the same tab key, from a draft to the thread that the draft became, or
 *   to the page that T3 shows in place of the page of the tab (a redirect without a click).
 * - [title] is the thread title that the page shows, or a fixed name for a draft and an app page.
 *   The tab shows it through [getPresentableName].
 */
class T3PageFile(path: String, title: String? = null) : LightVirtualFile("T3 page", T3PageFileType, "") {

    @Volatile
    var pagePath: String = path
        internal set(value) {
            require(T3Routes.tabPage(value) != null) { "Not a tab page" }
            field = value
        }

    @Volatile
    var title: String = title?.takeIf { it.isNotBlank() } ?: defaultTitle(path)

    init {
        require(T3Routes.tabPage(path) != null) { "Not a tab page" }
        isWritable = false
    }

    val page: T3Routes.TabPage get() = T3Routes.tabPage(pagePath)!!

    val threadRoute: T3Routes.ThreadRoute? get() = T3Routes.threadRoute(pagePath)

    override fun getPresentableName(): String = title

    companion object {
        /** The tab title before the page reports a thread title. */
        fun defaultTitle(path: String): String {
            val page = T3Routes.tabPage(path) ?: return T3Bundle["editor.untitled"]
            return when (page.kind) {
                T3Routes.PageKind.THREAD -> T3Bundle["editor.untitled"]
                T3Routes.PageKind.DRAFT -> T3Bundle["editor.newThread"]
                T3Routes.PageKind.PAGE -> when (page.key) {
                    "page:settings" -> T3Bundle["editor.settings"]
                    "page:pull-requests" -> T3Bundle["editor.pullRequests"]
                    "page:usage" -> T3Bundle["editor.usage"]
                    else -> "T3 " + page.key.removePrefix("page:")
                }
            }
        }
    }
}

/** File type of [T3PageFile]. The plugin does not register it: no file on disk has this type. */
object T3PageFileType : FileType {
    private val ICON: Icon = IconLoader.getIcon("/icons/toolWindow.svg", T3PageFileType::class.java)

    override fun getName(): String = "T3 Page"
    override fun getDescription(): String = T3Bundle["editor.fileType"]
    override fun getDefaultExtension(): String = ""
    override fun getIcon(): Icon = ICON
    override fun isBinary(): Boolean = true
    override fun isReadOnly(): Boolean = true
    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
}
