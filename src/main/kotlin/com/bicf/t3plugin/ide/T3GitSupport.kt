package com.bicf.t3plugin.ide

import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent

/**
 * The Git tools of the IDE. The plugin registers an implementation only when the IDE has the Git plugin
 * (`t3-git.xml`); without it, T3 keeps its own Git actions.
 *
 * Each function returns false when no Git repository of the IDE contains [directory], for example a T3 worktree
 * outside the project: T3 then does the job. Call them on the EDT.
 */
interface T3GitSupport {

    /** Opens the IDE commit (action "Commit…"). [context] gives the data context of the action. */
    fun commit(directory: VirtualFile, context: JComponent): Boolean

    /** Opens the IDE branches popup of the repository. */
    fun branches(directory: VirtualFile, context: JComponent): Boolean

    /** Shows the changes of the working tree against [ref], for example `origin/main`. */
    fun diffWithBranch(directory: VirtualFile, ref: String): Boolean
}
