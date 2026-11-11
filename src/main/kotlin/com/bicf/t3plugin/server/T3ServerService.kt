package com.bicf.t3plugin.server

import com.bicf.t3plugin.T3Exception
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.util.concurrent.atomic.AtomicReference

/**
 * Connects the IDE to a T3 server on this machine.
 *
 * Rules:
 * - The service uses a running server first: the desktop app server, or a `t3 serve` of another tool.
 * - When no server answers, the service starts `t3 serve --no-browser` on the same T3 home.
 * - When the IDE closes, the service stops only the server that it started.
 */
@Service(Service.Level.APP)
class T3ServerService : Disposable {

    private val connectLock = Any()
    private val ownedServer = AtomicReference<LaunchedServer?>()

    /**
     * Returns a T3 server that answers. Starts `t3 serve --no-browser` when no server answers.
     * Blocks for up to 60 s.
     *
     * @throws T3Exception when the `t3` command is missing or no server answers.
     */
    @RequiresBackgroundThread
    fun ensureServer(): T3Runtime = synchronized(connectLock) {
        val home = T3Home.detect()
        val connector = T3ServerConnector(
            readRuntime = { T3Runtime.read(home.runtimeFile) },
            isReachable = { it.isReachable() },
            launch = {
                val cli = T3Cli.detect(home) ?: throw T3Exception(
                    T3Exception.Reason.CLI_NOT_FOUND,
                    "The `t3` command is not in ${home.dir.resolve("bin")}, ~/.t3/bin or PATH.",
                )
                // Store the process at once: dispose() must stop it, also during the wait.
                T3ServeProcess.start(cli).also(ownedServer::set)
            },
            sleep = {
                ProgressManager.checkCanceled()
                Thread.sleep(it.toMillis())
            },
        )
        connector.connect().runtime
    }

    override fun dispose() {
        ownedServer.getAndSet(null)?.stop()
    }
}
