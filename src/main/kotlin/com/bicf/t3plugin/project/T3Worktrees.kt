package com.bicf.t3plugin.project

import com.bicf.t3plugin.server.T3Home
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * The Git worktrees that T3 creates for its threads (verified with T3 0.1.9).
 *
 * Rules:
 * - T3 creates the worktree of a thread under `<T3 home>/worktrees/<project>/<name>`. The worktree has a `.git` file
 *   with `gitdir: <main>/.git/worktrees/<name>`; `<main>` is the folder of the T3 project.
 * - The T3 CLI does not know a worktree path as a project ("No T3 Code project matches"). An IDE window of a T3 worktree
 *   uses the main folder for the CLI, and never adds the worktree as a T3 project.
 * - Any other folder, also a Git worktree outside the T3 home, is its own project.
 */
object T3Worktrees {

    private const val GITDIR = "gitdir:"

    /** The main folder of the T3 project when [path] is a T3 worktree, otherwise null. Reads the `.git` file. */
    fun mainFolder(path: Path, home: T3Home): Path? {
        val folder = path.toAbsolutePath().normalize()
        if (!folder.startsWith(home.worktreesDir.toAbsolutePath().normalize())) return null
        val text = try {
            val git = folder.resolve(".git")
            if (!Files.isRegularFile(git)) return null
            Files.readString(git)
        } catch (e: IOException) {
            return null
        }
        return mainFolderOf(text)
    }

    /** The folder for the T3 CLI: the main folder for a T3 worktree, otherwise [path]. */
    fun cliFolder(path: String, home: T3Home): String =
        runCatching { mainFolder(Path.of(path), home)?.toString() }.getOrNull() ?: path

    /** `<main>` from the text of a `.git` file `gitdir: <main>/.git/worktrees/<name>`, or null. */
    internal fun mainFolderOf(gitFile: String): Path? {
        val line = gitFile.lineSequence().map(String::trim).firstOrNull { it.startsWith(GITDIR) } ?: return null
        val gitDir = try {
            Path.of(line.removePrefix(GITDIR).trim()).normalize()
        } catch (e: InvalidPathException) {
            return null
        }
        val worktrees = gitDir.parent ?: return null
        val dotGit = worktrees.parent ?: return null
        if (worktrees.fileName?.toString() != "worktrees" || dotGit.fileName?.toString() != ".git") return null
        return dotGit.parent
    }
}
