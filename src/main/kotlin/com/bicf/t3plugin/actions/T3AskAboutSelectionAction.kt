package com.bicf.t3plugin.actions

import com.bicf.t3plugin.T3Bundle
import com.bicf.t3plugin.T3Exception
import com.bicf.t3plugin.editor.T3PageTabs
import com.bicf.t3plugin.project.T3ProjectBinder
import com.bicf.t3plugin.project.T3ProjectWindows
import com.bicf.t3plugin.project.T3Threads
import com.bicf.t3plugin.project.T3Worktrees
import com.bicf.t3plugin.server.T3Cli
import com.bicf.t3plugin.server.T3Home
import com.bicf.t3plugin.settings.T3Settings
import com.bicf.t3plugin.toolWindow.T3ProjectScope
import com.bicf.t3plugin.toolWindow.T3Routes
import com.bicf.t3plugin.toolWindow.T3ToolWindowFactory
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.nio.file.Path

/**
 * Editor action "Ask T3 About Selection".
 *
 * Rules:
 * - With editor tabs (Settings), the action sends the message to the thread of the last selected T3 tab that is still
 *   open (`t3 thread send`). Without editor tabs, it sends it to the thread on screen in the "T3 Code" tool window.
 * - Otherwise the action starts a new thread in the project (`t3 thread start`).
 * - The action then shows the thread: in its editor tab, or in the tool window without editor tabs.
 * - The message has the question, `<path>:<lines>` and the selected code. The path is relative to the project root.
 * - The action works only with the local server: the CLI cannot reach a remote server.
 */
class T3AskAboutSelectionAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = project?.basePath != null && editor != null &&
            editor.selectionModel.hasSelection() && T3Settings.getInstance().remoteOrigin == null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val basePath = project.basePath ?: return
        val selection = editor.selectionModel
        val code = selection.selectedText?.takeIf { it.isNotEmpty() } ?: return
        val document = editor.document
        val startLine = document.getLineNumber(selection.selectionStart) + 1
        val endLine = document.getLineNumber(maxOf(selection.selectionStart, selection.selectionEnd - 1)) + 1
        val file = FileDocumentManager.getInstance().getFile(document)
        val relativePath = file?.let { relativePath(basePath, it.path) } ?: "untitled"

        val question = Messages.showMultilineInputDialog(
            project, T3Bundle["action.ask.prompt"], T3Bundle["action.ask.title"], "", null, null,
        ) ?: return
        val message = T3SelectionMessage.build(question, relativePath, startLine, endLine, file?.extension.orEmpty(), code)

        val scope = project.service<T3ProjectScope>()
        val inTabs = T3Settings.getInstance().openThreadsInEditor
        val openThreadId = (if (inTabs) project.service<T3PageTabs>().lastThreadId() else scope.browserPanel?.currentThreadId())
            ?: service<T3ProjectWindows>().worktreeThread(basePath)
        scope.coroutineScope.launch(Dispatchers.EDT) {
            try {
                val (threadId, environmentId) = runInterruptible(Dispatchers.IO) {
                    val home = T3Home.detect()
                    val cli = T3Cli.require(home)
                    val id = if (openThreadId != null) {
                        T3Threads.send(cli, openThreadId, message)
                        openThreadId
                    } else {
                        // The CLI does not know a worktree outside the T3 projects: the thread starts in the main folder.
                        T3Threads.start(cli, T3Worktrees.cliFolder(cli, basePath), message)
                    }
                    id to T3ProjectBinder.environmentId(home)
                }
                val route = environmentId?.let { T3Routes.threadPath(it, threadId) }?.let(T3Routes::threadRoute)
                if (inTabs && route != null) {
                    project.service<T3PageTabs>().open(route.path)
                } else {
                    ToolWindowManager.getInstance(project).getToolWindow(T3ToolWindowFactory.ID)?.activate {
                        project.service<T3ProjectScope>().browserPanel?.openThread(threadId)
                    }
                }
            } catch (error: T3Exception) {
                NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                    .createNotification(T3Bundle.message("action.ask.failed", error.message.orEmpty()), NotificationType.ERROR)
                    .notify(project)
            }
        }
    }

    private fun relativePath(basePath: String, path: String): String =
        runCatching { Path.of(basePath).relativize(Path.of(path)).toString().replace('\\', '/') }.getOrDefault(path)

    private companion object {
        const val NOTIFICATION_GROUP = "T3 Code"
    }
}
