package com.bicf.t3plugin.server

import com.bicf.t3plugin.T3Exception
import com.bicf.t3plugin.T3Exception.Reason
import java.time.Duration

/** A `t3 serve` process that the plugin started. */
interface LaunchedServer {
    /** The exit code, or null while the process runs. */
    fun exitCodeOrNull(): Int?

    /** Stops the process and its child processes. Does not wait. */
    fun stop()
}

/** A server that answers. [ownedServer] is the process that the plugin started, or null for another server. */
class T3Connection(val runtime: T3Runtime, val ownedServer: LaunchedServer?)

/**
 * Finds a T3 server that answers, or starts one.
 *
 * Rules:
 * - A runtime file with a live pid and an open port is a server that answers. The connector does not start a second server.
 * - Exit 78 (`ServerAlreadyRunningError`) means that another process owns the T3 home. The connector waits for that server.
 * - Any other exit code stops the wait at once.
 * - After [startTimeout], the connector stops the process that it started.
 */
class T3ServerConnector(
    private val readRuntime: () -> T3Runtime?,
    private val isReachable: (T3Runtime) -> Boolean,
    private val launch: () -> LaunchedServer,
    private val startTimeout: Duration = Duration.ofSeconds(60),
    private val pollInterval: Duration = Duration.ofMillis(250),
    private val nanoTime: () -> Long = System::nanoTime,
    private val sleep: (Duration) -> Unit = { Thread.sleep(it.toMillis()) },
) {

    fun connect(): T3Connection {
        reachableRuntime()?.let { return T3Connection(it, ownedServer = null) }

        val server = launch()
        val deadline = nanoTime() + startTimeout.toNanos()
        while (true) {
            val exitCode = server.exitCodeOrNull()
            reachableRuntime()?.let { return T3Connection(it, ownedServer = server.takeIf { exitCode == null }) }
            if (exitCode != null && exitCode != EXIT_SERVER_ALREADY_RUNNING) {
                throw T3Exception(Reason.SERVER_EXITED, "`t3 serve` stopped with exit code $exitCode before a server answered.")
            }
            if (nanoTime() - deadline >= 0) {
                server.stop()
                if (exitCode == EXIT_SERVER_ALREADY_RUNNING) {
                    throw T3Exception(
                        Reason.SERVER_OWNED_ELSEWHERE,
                        "Another T3 server owns the T3 home, but it did not answer within ${startTimeout.seconds} s.",
                    )
                }
                throw T3Exception(Reason.SERVER_TIMEOUT, "No T3 server answered within ${startTimeout.seconds} s.")
            }
            sleep(pollInterval)
        }
    }

    private fun reachableRuntime(): T3Runtime? = readRuntime()?.takeIf(isReachable)

    companion object {
        /** Exit code of `t3 serve` when another server owns the T3 home (`ServerAlreadyRunningError`). */
        const val EXIT_SERVER_ALREADY_RUNNING = 78
    }
}
