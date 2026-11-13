package com.bicf.t3plugin.ide

import com.bicf.t3plugin.T3Bundle
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.changes.patch.tool.PatchDiffRequest
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import javax.swing.JComponent

/**
 * Does the job of a T3 tool with the tool of the IDE (see [T3IdeBridge]).
 *
 * Rules:
 * - File: an existing file opens in the IDE editor at the line and column. A missing file is not handled.
 * - Directory (only from `shell.openInEditor`): the Project view selects it, when it is in the project content.
 * - Turn diff: the IDE diff viewer shows each file of the patch, from the file that the user clicked.
 * - Terminal, Git: only with the Terminal or Git plugin of the IDE, and Git only for a repository of the IDE project.
 * - Any error means "not handled": T3 does the job. The plugin logs the error without the request content.
 */
@Service(Service.Level.PROJECT)
class T3IdeTools(private val project: Project, private val scope: CoroutineScope) {

    /** Handles [request]. [context] is the browser component of the page. [answer] runs on the EDT. */
    fun handle(request: T3IdeRequest, context: JComponent, answer: (Boolean) -> Unit) {
        scope.launch(Dispatchers.EDT) {
            val handled = try {
                perform(request, context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                LOG.warn("The IDE could not handle a T3 ${request::class.simpleName} request", e)
                false
            }
            answer(handled)
        }
    }

    private suspend fun perform(request: T3IdeRequest, context: JComponent): Boolean = when (request) {
        is T3IdeRequest.OpenFile -> open(request.target, directories = false)
        is T3IdeRequest.OpenInEditor -> open(request.target, directories = true)
        is T3IdeRequest.TurnDiff -> showTurnDiff(request)
        is T3IdeRequest.Terminal -> {
            val terminal = project.serviceOrNull<T3TerminalSupport>()
            val directory = terminal?.let { find(request.cwd) }?.takeIf { it.isDirectory }
            directory != null && terminal.open(directory.toNioPath(), T3Bundle.message("terminal.tabName", directory.name))
        }
        is T3IdeRequest.Files -> find(request.cwd)?.let { selectInProjectView(it) } ?: false
        is T3IdeRequest.Commit -> git { it.commit(dir(request.cwd) ?: return false, context) }
        is T3IdeRequest.Branches -> git { it.branches(dir(request.cwd) ?: return false, context) }
        is T3IdeRequest.BranchDiff -> git { it.diffWithBranch(dir(request.cwd) ?: return false, request.ref) }
    }

    private inline fun git(block: (T3GitSupport) -> Boolean): Boolean = project.serviceOrNull<T3GitSupport>()?.let(block) ?: false

    private suspend fun open(target: T3FileTarget, directories: Boolean): Boolean {
        val file = find(target.path) ?: return false
        if (file.isDirectory) return directories && selectInProjectView(file)
        val line = target.line?.minus(1)
        val descriptor = if (line == null) {
            OpenFileDescriptor(project, file)
        } else {
            OpenFileDescriptor(project, file, line, (target.column ?: 1) - 1)
        }
        descriptor.navigate(true)
        return true
    }

    private suspend fun selectInProjectView(file: VirtualFile): Boolean {
        val inContent = readAction { ProjectFileIndex.getInstance(project).isInContent(file) }
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)
        if (!inContent || toolWindow == null) return false
        toolWindow.activate { ProjectView.getInstance(project).select(null, file, true) }
        return true
    }

    private fun showTurnDiff(request: T3IdeRequest.TurnDiff): Boolean {
        val patches: List<TextFilePatch> = runCatching { PatchReader(request.patch).readTextPatches() }.getOrNull().orEmpty()
        if (patches.isEmpty()) return false
        val from = request.fromTurn
        val to = request.toTurn
        val before = when {
            to != null && (from == null || from == to - 1) -> T3Bundle.message("diff.beforeTurn", to)
            from != null -> T3Bundle.message("diff.afterTurn", from)
            else -> T3Bundle["diff.before"]
        }
        val after = to?.let { T3Bundle.message("diff.afterTurn", it) } ?: T3Bundle["diff.after"]
        val paths = patches.map { T3PatchFiles.clean(it.afterName) ?: T3PatchFiles.clean(it.beforeName) ?: "?" }
        val requests = patches.mapIndexed { index, patch -> PatchDiffRequest(patch, paths[index], before, after) }
        val chain = SimpleDiffRequestChain(requests, T3PatchFiles.indexOf(paths, request.fileHint))
        DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT)
        return true
    }

    private suspend fun dir(path: String): VirtualFile? = find(path)?.takeIf { it.isDirectory }

    /** The local file at [path], or null when [path] is not absolute or the file does not exist. */
    private suspend fun find(path: String): VirtualFile? {
        val local: Path = T3FileTarget(path).toLocalPath() ?: return null
        return withContext(Dispatchers.IO) { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(local) }
    }

    private companion object {
        val LOG = logger<T3IdeTools>()
    }
}
