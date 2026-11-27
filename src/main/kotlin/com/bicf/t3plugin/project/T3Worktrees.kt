package com.bicf.t3plugin.project

import com.bicf.t3plugin.server.T3Cli
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Duration

/**
 * The Git worktrees of the T3 projects: the worktrees that T3 creates for its threads, and the worktrees that the IDE
 * or `git worktree add` creates (verified with T3 0.1.9).
 *
 * Rules:
 * - A worktree is a folder with a `.git` file `gitdir: <main>/.git/worktrees/<name>`, in any location. `<main>` is the
 *   main folder of the repository. A relative `gitdir` starts in the worktree folder.
 * - T3 creates the worktree of a thread under `<worktrees directory>/<project>/<name>` (see
 *   [com.bicf.t3plugin.server.T3Home.worktreesDir]). The IDE creates its worktrees in the folder that the user selects.
 * - The T3 CLI knows a folder only when a T3 project contains it ("No T3 Code project matches"). For a worktree that no
 *   T3 project contains, the CLI uses the main folder.
 * - An IDE window of a worktree never adds the worktree as a T3 project: T3 would show it as a separate project.
 */
object T3Worktrees {

    private const val GITDIR = "gitdir:"

    /** The main folder of the repository when [path] is a linked Git worktree, otherwise null. Reads the `.git` file. */
    fun mainFolder(path: Path): Path? {
        val folder = path.toAbsolutePath().normalize()
        val text = try {
            val git = folder.resolve(".git")
            if (!Files.isRegularFile(git)) return null
            Files.readString(git)
        } catch (e: IOException) {
            return null
        }
        return mainFolderOf(text, folder)
    }

    /** [mainFolder] for a path text; null also for a path that is not valid. */
    fun mainFolder(path: String?): Path? = path?.takeIf { it.isNotBlank() }?.let {
        try {
            mainFolder(Path.of(it))
        } catch (e: InvalidPathException) {
            null
        }
    }

    /**
     * The folder for the T3 CLI: [path] when it is not a worktree or a T3 project contains it, otherwise the main
     * folder. Blocks for up to 1 CLI call, only for a worktree.
     */
    fun cliFolder(cli: T3Cli, path: String, timeout: Duration = Duration.ofSeconds(30)): String {
        val main = mainFolder(path) ?: return path
        val list = cli.run(timeout, "thread", "list", "--project", path, "--json")
        return if (list.exitCode == 0) path else main.toString()
    }

    /**
     * `<main>` from the text of a `.git` file `gitdir: <main>/.git/worktrees/<name>`, or null.
     * [folder] is the worktree folder, for a relative `gitdir`.
     */
    internal fun mainFolderOf(gitFile: String, folder: Path? = null): Path? {
        val line = gitFile.lineSequence().map(String::trim).firstOrNull { it.startsWith(GITDIR) } ?: return null
        val gitDir = try {
            val value = Path.of(line.removePrefix(GITDIR).trim())
            when {
                value.isAbsolute -> value
                folder != null -> folder.resolve(value)
                else -> return null
            }.normalize()
        } catch (e: InvalidPathException) {
            return null
        }
        val worktrees = gitDir.parent ?: return null
        val dotGit = worktrees.parent ?: return null
        if (worktrees.fileName?.toString() != "worktrees" || dotGit.fileName?.toString() != ".git") return null
        return dotGit.parent
    }
}
