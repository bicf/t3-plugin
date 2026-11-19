package com.bicf.t3plugin.ide.git

import com.bicf.t3plugin.ide.T3GitSupport
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import git4idea.branch.GitBrancher
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import javax.swing.JComponent

/** [T3GitSupport] with the Git plugin of the IDE. Registered in `t3-git.xml`. */
internal class T3GitSupportImpl(private val project: Project) : T3GitSupport {

    override fun commit(directory: VirtualFile, context: JComponent): Boolean =
        repository(directory) != null && run(COMMIT_ACTION, context)

    override fun branches(directory: VirtualFile, context: JComponent): Boolean =
        repository(directory) != null && run(BRANCHES_ACTION, context)

    override fun diffWithBranch(directory: VirtualFile, ref: String): Boolean {
        val repository = repository(directory) ?: return false
        GitBrancher.getInstance(project).showDiffWithLocal(ref, listOf(repository))
        return true
    }

    override fun reloadWorktrees() {
        GitRepositoryManager.getInstance(project).repositories.forEach { it.workingTreeHolder.scheduleReload() }
    }

    private fun repository(directory: VirtualFile): GitRepository? =
        GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(directory)

    /**
     * Runs [actionId] now. The action manager runs no action for a component that is not showing (a T3 tab in the
     * background), so the IDE frame gives the data context then. Returns false when the action did not run.
     */
    private fun run(actionId: String, context: JComponent): Boolean {
        val action = ActionManager.getInstance().getAction(actionId) ?: return false
        val component = context.takeIf { it.isShowing } ?: WindowManager.getInstance().getFrame(project)?.rootPane ?: return false
        val result = ActionManager.getInstance().tryToExecute(action, null, component, ActionPlaces.UNKNOWN, true)
        return !result.isRejected
    }

    private companion object {
        /** "Commit…" (Ctrl+K) of the platform VCS. */
        const val COMMIT_ACTION = "CheckinProject"

        /** "Branches…" of the Git plugin. */
        const val BRANCHES_ACTION = "Git.Branches"
    }
}
