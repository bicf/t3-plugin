package com.bicf.t3plugin.server

import com.bicf.t3plugin.T3Exception
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.util.SystemInfo
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
 * Every command gets `--base-dir <home>`, so the CLI and the plugin always use the same home.
 */
class T3Cli(
    val executable: Path,
    val home: T3Home,
    private val workingDirectory: Path = T3Home.userHome(),
) {

    fun command(vararg args: String): GeneralCommandLine =
        GeneralCommandLine(listOf(executable.toString()) + args + listOf("--base-dir", home.dir.toString()))
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withWorkingDirectory(workingDirectory)

    /** Output of a short command. [exitCode] is null when the command did not stop within the timeout. */
    class Result(val exitCode: Int?, val stdout: String, val stderr: String)

    /** Runs a short command, closes its stdin and returns its output. Blocks for up to [timeout]. */
    fun run(timeout: Duration, vararg args: String): Result {
        val process = try {
            command(*args).toProcessBuilder().start()
        } catch (e: ExecutionException) {
            throw startFailed(e)
        } catch (e: IOException) {
            throw startFailed(e)
        }
        process.outputStream.close()
        val stdout = process.inputStream.readInBackground()
        val stderr = process.errorStream.readInBackground()
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.descendants().toList().forEach(ProcessHandle::destroyForcibly)
            process.destroyForcibly()
            return Result(null, "", "")
        }
        return Result(process.exitValue(), stdout.textOrEmpty(), stderr.textOrEmpty())
    }

    private fun startFailed(cause: Exception) =
        T3Exception(T3Exception.Reason.CLI_START_FAILED, "Could not start `$executable`: ${cause.message}", cause)

    companion object {
        /** Finds the `t3` command for [home]. Returns null when the command is missing. */
        fun detect(home: T3Home): T3Cli? {
            val executable = locate(home, T3Home.userHome(), SystemInfo.isWindows) { name ->
                PathEnvironmentVariableUtil.findInPath(name)?.toPath()
            } ?: return null
            return T3Cli(executable, home)
        }

        /**
         * Search order: `<home>/bin`, `~/.t3/bin` (the desktop app writes its wrapper script there), then `PATH`.
         * The Windows file names are not verified.
         */
        fun locate(home: T3Home, userHome: Path, isWindows: Boolean, findInPath: (String) -> Path?): Path? {
            val names = if (isWindows) listOf("t3.cmd", "t3.exe") else listOf("t3")
            val dirs = listOf(home.dir.resolve("bin"), userHome.resolve(".t3").resolve("bin")).distinct()
            return dirs.flatMap { dir -> names.map(dir::resolve) }
                .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
                ?: names.firstNotNullOfOrNull(findInPath)
        }

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
