package com.bicf.t3plugin.server

import com.bicf.t3plugin.T3Exception
import com.bicf.t3plugin.settings.T3Settings
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.EnvironmentUtil
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The `t3` command and the T3 home that it works on.
 * Every command gets `T3CODE_HOME=<home>`, so the CLI and the plugin always use the same home.
 * The plugin uses the variable, not `--base-dir`, so that no option comes after a positional argument.
 */
class T3Cli(
    val executable: Path,
    val home: T3Home,
    private val workingDirectory: Path = T3Home.userHome(),
) {

    fun command(args: List<String>): GeneralCommandLine =
        GeneralCommandLine(listOf(executable.toString()) + args)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment(T3Home.HOME_ENV, home.dir.toString())
            .withWorkingDirectory(workingDirectory)

    fun command(vararg args: String): GeneralCommandLine = command(args.toList())

    /** Output of a short command. [exitCode] is null when the command did not stop within the timeout. */
    class Result(val exitCode: Int?, val stdout: String, val stderr: String) {
        /** The last line of stderr that is not blank, at most 300 characters. */
        val lastErrorLine: String
            get() = stderr.lineSequence().lastOrNull { it.isNotBlank() }?.trim()?.take(300).orEmpty()
    }

    /** Runs a short command, writes [stdin] (or nothing) and returns its output. Blocks for up to [timeout]. */
    fun run(timeout: Duration, args: List<String>, stdin: String? = null): Result {
        val process = try {
            command(args).toProcessBuilder().start()
        } catch (e: ExecutionException) {
            throw startFailed(e)
        } catch (e: IOException) {
            throw startFailed(e)
        }
        val stdout = process.inputStream.readInBackground()
        val stderr = process.errorStream.readInBackground()
        try {
            process.outputStream.use { input -> if (stdin != null) input.write(stdin.toByteArray(Charsets.UTF_8)) }
        } catch (e: IOException) {
            // The command stopped before it read stdin. Its exit code tells the caller what happened.
        }
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.descendants().toList().forEach(ProcessHandle::destroyForcibly)
            process.destroyForcibly()
            return Result(null, "", "")
        }
        return Result(process.exitValue(), stdout.textOrEmpty(), stderr.textOrEmpty())
    }

    fun run(timeout: Duration, vararg args: String): Result = run(timeout, args.toList())

    private fun startFailed(cause: Exception) =
        T3Exception(T3Exception.Reason.CLI_START_FAILED, "Could not start `$executable`: ${cause.message}", cause)

    companion object {
        /** Finds the `t3` command for [home]. Returns null when the command is missing. */
        fun detect(home: T3Home): T3Cli? {
            val executable = locate(home, T3Home.userHome(), SystemInfo.isWindows, T3Settings.getInstance().cliPath) { name ->
                findInPath(EnvironmentUtil.getValue("PATH"), name)
            } ?: return null
            return T3Cli(executable, home)
        }

        /** The first executable file [name] in the directories of [path]. The plugin does not use the deprecated platform lookup. */
        internal fun findInPath(path: String?, name: String): Path? =
            path.orEmpty().split(File.pathSeparatorChar).asSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { dir -> runCatching { Path.of(dir, name) }.getOrNull() }
                .firstOrNull(::isExecutableFile)

        /** Like [detect], but throws [T3Exception] with [T3Exception.Reason.CLI_NOT_FOUND] when the command is missing. */
        fun require(home: T3Home): T3Cli {
            detect(home)?.let { return it }
            val configured = T3Settings.getInstance().cliPath
            val message = if (configured != null) {
                "The `t3` command in Settings | Tools | T3 Code does not exist or is not executable: $configured."
            } else {
                "The `t3` command is not in ${home.dir.resolve("bin")}, ~/.t3/bin or PATH."
            }
            throw T3Exception(T3Exception.Reason.CLI_NOT_FOUND, message)
        }

        /**
         * A [configured] path wins and has no fallback: when it is not executable, the result is null.
         * Otherwise the search order is `<home>/bin`, `~/.t3/bin` (the desktop app writes its wrapper script there), then `PATH`.
         * The Windows file names are not verified.
         */
        fun locate(
            home: T3Home,
            userHome: Path,
            isWindows: Boolean,
            configured: Path? = null,
            findInPath: (String) -> Path?,
        ): Path? {
            if (configured != null) return configured.takeIf(::isExecutableFile)
            val names = if (isWindows) listOf("t3.cmd", "t3.exe") else listOf("t3")
            val dirs = listOf(home.dir.resolve("bin"), userHome.resolve(".t3").resolve("bin")).distinct()
            return dirs.flatMap { dir -> names.map(dir::resolve) }.firstOrNull(::isExecutableFile)
                ?: names.firstNotNullOfOrNull(findInPath)
        }

        private fun isExecutableFile(path: Path): Boolean = Files.isRegularFile(path) && Files.isExecutable(path)

        private fun InputStream.readInBackground(): CompletableFuture<String> {
            val text = CompletableFuture<String>()
            thread(isDaemon = true, name = "t3-cli-output") {
                try {
                    text.complete(use { it.readAllBytes() }.toString(Charsets.UTF_8))
                } catch (e: IOException) {
                    text.completeExceptionally(e)
                }
            }
            return text
        }

        private fun CompletableFuture<String>.textOrEmpty(): String =
            runCatching { get(5, TimeUnit.SECONDS) }.getOrDefault("")
    }
}
