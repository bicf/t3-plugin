package com.bicf.t3plugin.project

import com.bicf.t3plugin.editor.T3PageTabs
import com.bicf.t3plugin.ide.T3GitSupport
import com.bicf.t3plugin.settings.T3Settings
import com.bicf.t3plugin.toolWindow.T3Routes
import com.bicf.t3plugin.toolWindow.T3ToolWindowFactory
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.ToolWindowManager
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Links the T3 projects to the IDE windows: one IDE window per T3 project, and one per T3 worktree of a new thread.
 * Only with the T3 server on this machine: the folders of a remote server are on another machine.
 *
 * Rules:
 * - "Open T3 projects in IDE windows" (Settings):
 *   - A new T3 project ("Add existing project", "New project", "Turn into project") opens its folder in a new IDE
 *     window, with the "T3 Code" tool window. A project that an IDE window shows already opens nothing.
 *   - A thread or a draft that the user selects in the T3 sidebar opens in the IDE window of its folder: the window of
 *     its worktree when that window is open, otherwise the window of its project. When that window is not open, the
 *     IDE opens it. A page without a known project, or of a folder that does not exist, opens in the window of the
 *     sidebar.
 * - "Open the worktree of a new thread in an IDE window" (Settings): when a thread of the user gets a T3 worktree
 *   (see [T3ShellModel]), the worktree opens in a new IDE window as a Git worktree of the project (with the Git plugin,
 *   as "Open" in the "Worktrees" tab of the Git tool window), and the thread opens there in an editor tab.
 * - The plugin opens the editor tab of a new window when the window is ready (see [Startup]).
 * Call the functions on the EDT, except [onProjectOpened].
 */
@Service(Service.Level.APP)
class T3ProjectWindows {

    private val model = T3ShellModel()

    /** Folder of a window that the plugin opens → the page for its editor tab ("" for none). */
    private val pending = ConcurrentHashMap<String, String>()

    /** Folder of a worktree window that the plugin opened → its thread. */
    private val worktreeThreads = ConcurrentHashMap<String, String>()

    /** A message of the shell watch of the sidebar of [reporter]. [environmentId] builds the thread routes. */
    fun onShellMessage(reporter: Project, environmentId: String?, json: String) {
        val update = T3ShellModel.parse(json) ?: return
        val changes = synchronized(model) { model.apply(update) }
        val settings = T3Settings.getInstance()
        for (change in changes) {
            when (change) {
                is T3ShellModel.Change.ProjectAdded ->
                    if (settings.openProjectsInWindows) open(reporter, folder(change.project.root), page = null, worktree = false)
                is T3ShellModel.Change.WorktreeCreated -> if (settings.openWorktreesInWindows) {
                    val folder = folder(change.thread.worktreePath) ?: continue
                    worktreeThreads[key(folder)] = change.thread.id
                    val page = environmentId?.let { T3Routes.threadPath(it, change.thread.id) }
                    val context = change.projectRoot?.let(::openProject) ?: reporter
                    open(context, folder, page, worktree = true)
                }
            }
        }
    }

    /**
     * The folder of the IDE window of [pagePath] when it is not the window of [project], otherwise null.
     * [draftProjectId] and [draftWorktree] come from the draft of a `/draft/<id>` page.
     */
    fun targetFolder(project: Project, pagePath: String, draftProjectId: String?, draftWorktree: String?): Path? {
        if (!T3Settings.getInstance().openProjectsInWindows) return null
        val base = project.basePath ?: return null
        val page = T3Routes.tabPage(pagePath) ?: return null
        val (worktree, projectId) = when (page.kind) {
            T3Routes.PageKind.THREAD -> {
                val id = T3Routes.threadRoute(pagePath)?.threadId ?: return null
                val thread = synchronized(model) { model.thread(id) } ?: return null
                thread.worktreePath to thread.projectId
            }
            T3Routes.PageKind.DRAFT -> draftWorktree to (draftProjectId ?: return null)
            T3Routes.PageKind.PAGE -> return null
        }
        val root = synchronized(model) { model.projectRoot(projectId) } ?: return null
        val target = worktree?.takeIf { openProject(it) != null } ?: root
        if (FileUtil.pathsEqual(target, base)) return null
        return folder(target)?.takeIf { Files.isDirectory(it) }
    }

    /** Opens [pagePath] in an editor tab of the window of [folder]. Opens the window when it is not open. */
    fun openPage(context: Project, folder: Path, pagePath: String) = open(context, folder, pagePath, worktree = false, focus = true)

    /**
     * The thread of the T3 worktree [basePath]: the thread of a worktree window that the plugin opened, otherwise a
     * thread of the sidebar with this worktree. Null for another folder.
     */
    fun worktreeThread(basePath: String?): String? {
        val folder = folder(basePath) ?: return null
        worktreeThreads[key(folder)]?.let { return it }
        return synchronized(model) { model.threadInWorktree(folder.toString(), FileUtil::pathsEqual)?.id }
    }

    /** The page for the editor tab of [project], when the plugin opened its window. Runs on any thread. */
    internal fun onProjectOpened(project: Project) {
        val folder = folder(project.basePath) ?: return
        val page = pending.remove(key(folder)) ?: return
        showInWindow(project, page.ifEmpty { null }, showToolWindow = true)
    }

    /**
     * Opens [folder] in an IDE window, then [page] in an editor tab of that window. A window that is open already gets
     * the focus and the tab only when [focus] is true; otherwise nothing happens.
     */
    private fun open(context: Project, folder: Path?, page: String?, worktree: Boolean, focus: Boolean = false) {
        if (folder == null) return
        // The page messages run in any modality; a project opens only outside a modal dialog.
        ApplicationManager.getApplication().invokeLater({
            if (context.isDisposed) return@invokeLater
            val existing = openProject(folder.toString())
            if (existing != null) {
                if (!focus) return@invokeLater
                ProjectUtil.focusProjectWindow(existing, true)
                showInWindow(existing, page, showToolWindow = false)
                return@invokeLater
            }
            if (!Files.isDirectory(folder)) return@invokeLater
            pending[key(folder)] = page.orEmpty()
            try {
                // As "Open" in the "Worktrees" tab of the Git tool window: reload the worktrees, then a new window.
                if (worktree) context.serviceOrNull<T3GitSupport>()?.reloadWorktrees()
                val opened = ProjectUtil.openOrImport(folder, null, true) ?: return@invokeLater
                onProjectOpened(opened)
            } catch (e: ProcessCanceledException) {
                pending.remove(key(folder))
                throw e
            } catch (e: Exception) {
                pending.remove(key(folder))
                LOG.warn("The IDE could not open a T3 folder in a new window", e)
            }
        }, ModalityState.nonModal())
    }

    private fun showInWindow(project: Project, page: String?, showToolWindow: Boolean) {
        val manager = ToolWindowManager.getInstance(project)
        manager.invokeLater {
            if (project.isDisposed) return@invokeLater
            if (showToolWindow) manager.getToolWindow(T3ToolWindowFactory.ID)?.show()
            if (page != null) project.service<T3PageTabs>().open(page)
        }
    }

    private fun openProject(path: String): Project? = ProjectManager.getInstance().openProjects.firstOrNull { project ->
        !project.isDisposed && project.basePath?.let { FileUtil.pathsEqual(it, path) } == true
    }

    private fun key(folder: Path): String = FileUtil.toCanonicalPath(folder.toString())

    private fun folder(path: String?): Path? = try {
        path?.takeIf { it.isNotBlank() }?.let { Path.of(it).toAbsolutePath().normalize() }
    } catch (e: InvalidPathException) {
        null
    }

    /** Opens the editor tab of a window that the plugin opened. Registered in plugin.xml. */
    internal class Startup : ProjectActivity {
        override suspend fun execute(project: Project) {
            serviceIfCreated<T3ProjectWindows>()?.onProjectOpened(project)
        }
    }

    companion object {
        private val LOG = logger<T3ProjectWindows>()

        /** True when the sidebar must report its projects and threads: a local server and one of the 2 settings. */
        fun watchEnabled(remote: Boolean): Boolean {
            if (remote) return false
            val settings = T3Settings.getInstance()
            return settings.openProjectsInWindows || settings.openWorktreesInWindows
        }
    }
}
