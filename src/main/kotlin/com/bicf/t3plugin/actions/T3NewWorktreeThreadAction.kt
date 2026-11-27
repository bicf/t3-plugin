package com.bicf.t3plugin.actions

import com.bicf.t3plugin.T3Bundle
import com.bicf.t3plugin.editor.T3PageTabs
import com.bicf.t3plugin.project.T3ProjectBinder
import com.bicf.t3plugin.project.T3ProjectWindows
import com.bicf.t3plugin.project.T3Worktrees
import com.bicf.t3plugin.server.T3Home
import com.bicf.t3plugin.settings.T3Settings
import com.bicf.t3plugin.toolWindow.T3ProjectScope
import com.bicf.t3plugin.toolWindow.T3Routes
import com.bicf.t3plugin.toolWindow.T3ThreadMenu
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/**
 * Action "New T3 Thread in This Worktree", in the IDE window of a Git worktree.
 *
 * Rules:
 * - The action shows only in the window of a Git worktree (see [T3Worktrees]), with the local server and editor tabs.
 *   It is enabled when a T3 thread uses the worktree of the window (see [T3ProjectWindows.worktreeThread]).
 * - The action opens the tab of that thread, then runs its T3 thread action "New thread on <branch>"
 *   ([NEW_THREAD_ON_BRANCH]). T3 opens a draft on the branch and in the folder of the worktree: the new thread uses the
 *   worktree of the window, not a new worktree and not the main folder.
 * - The action waits up to [WAIT_MS] for the thread page. When the page does not offer the T3 action, a notification
 *   tells the user.
 */
class T3NewWorktreeThreadAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val basePath = e.project?.basePath
        val settings = T3Settings.getInstance()
        val available = basePath != null && settings.remoteOrigin == null && settings.openThreadsInEditor &&
            T3Worktrees.mainFolder(basePath) != null
        e.presentation.isVisible = available
        e.presentation.isEnabled = available && service<T3ProjectWindows>().worktreeThread(basePath) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val threadId = service<T3ProjectWindows>().worktreeThread(project.basePath) ?: return
        project.service<T3ProjectScope>().coroutineScope.launch(Dispatchers.EDT) {
            val environmentId = runInterruptible(Dispatchers.IO) { T3ProjectBinder.environmentId(T3Home.detect()) }
            val path = environmentId?.let { T3Routes.threadPath(it, threadId) }
            val file = path?.let { project.service<T3PageTabs>().open(it) }
            if (file == null || !runWhenReady(file::threadMenu)) notifyFailure(project)
        }
    }

    /** Runs [NEW_THREAD_ON_BRANCH] in the page of [menu] when the page offers it. False after [WAIT_MS]. */
    private suspend fun runWhenReady(menu: () -> T3ThreadMenu.Host?): Boolean {
        val end = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < end) {
            val host = menu()
            val items = host?.let { runInterruptible(Dispatchers.IO) { it.items(ITEMS_WAIT_MS) } }
            if (host != null && items.orEmpty().any { it.id == NEW_THREAD_ON_BRANCH && it.enabled }) {
                withContext(Dispatchers.EDT) { host.run(NEW_THREAD_ON_BRANCH) }
                return true
            }
            delay(RETRY_MS)
        }
        return false
    }

    private fun notifyFailure(project: Project) {
        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(T3Bundle["action.newWorktreeThread.failed"], NotificationType.WARNING)
            .notify(project)
    }

    companion object {
        const val ID = "com.bicf.t3plugin.NewWorktreeThread"

        /** The id of the T3 thread action "New thread on <branch>" (T3 0.1.9). */
        const val NEW_THREAD_ON_BRANCH = "new-thread-on-branch"

        private const val NOTIFICATION_GROUP = "T3 Code"
        private const val WAIT_MS = 20_000L
        private const val ITEMS_WAIT_MS = 400L
        private const val RETRY_MS = 250L
    }
}
