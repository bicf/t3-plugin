package com.bicf.t3plugin.server

import com.bicf.t3plugin.T3Exception
import com.intellij.execution.ExecutionException
import java.io.IOException

/** Starts `t3 serve --no-browser`. */
object T3ServeProcess {

    /**
     * Starts the server and discards its output: `t3 serve` prints a pairing token, and the plugin never stores it.
     * To see why a server stops, run `t3 serve --no-browser` in a terminal.
     */
    fun start(cli: T3Cli, extraArgs: List<String> = emptyList()): LaunchedServer {
        val process = try {
            cli.command("serve", "--no-browser", *extraArgs.toTypedArray())
                .toProcessBuilder()
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (e: ExecutionException) {
            throw startFailed(cli, e)
        } catch (e: IOException) {
            throw startFailed(cli, e)
        }
        return ProcessServer(process)
    }

    private fun startFailed(cli: T3Cli, cause: Exception) =
        T3Exception(T3Exception.Reason.CLI_START_FAILED, "Could not start `${cli.executable} serve`: ${cause.message}", cause)

    private class ProcessServer(private val process: Process) : LaunchedServer {
        override fun exitCodeOrNull(): Int? = if (process.isAlive) null else process.exitValue()

        override fun stop() {
            // `t3` is a wrapper script: the server is a child process.
            // Take the list first, because the children leave the tree when the script stops.
            val children = process.descendants().toList()
            children.forEach(ProcessHandle::destroy)
            process.destroy()
        }
    }
}
