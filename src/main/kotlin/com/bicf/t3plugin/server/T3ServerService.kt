package com.bicf.t3plugin.server

import com.bicf.t3plugin.T3Exception
import com.bicf.t3plugin.settings.T3Settings
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/**
 * Connects the IDE to a T3 server on this machine.
 *
 * Rules:
 * - The service uses a running server first: the desktop app server, or a `t3 serve` of another tool.
 * - When no server answers and automatic start is on, the service starts `t3 serve --no-browser` on the same T3 home.
 * - When the IDE closes, the service stops only the server that it started.
 * - [state] feeds the status bar. A check runs every 10 s after [startMonitoring].
 */
@Service(Service.Level.APP)
class T3ServerService(private val coroutineScope: CoroutineScope) : Disposable {

    private val connectLock = Any()
    private val ownedServer = AtomicReference<LaunchedServer?>()
    private val monitoring = AtomicBoolean(false)
    private val mutableState = MutableStateFlow<T3ServerState>(T3ServerState.Unknown)

    val state: StateFlow<T3ServerState> = mutableState.asStateFlow()

    /**
     * Returns a T3 server that answers. Starts `t3 serve --no-browser` when no server answers and automatic start is on.
     * Blocks for up to 60 s.
     *
     * @throws T3Exception when no server answers.
     */
    @RequiresBackgroundThread
    fun ensureServer(): T3Runtime = synchronized(connectLock) {
        val home = T3Home.detect()
        val autoStart = T3Settings.getInstance().autoStartServer
        val connector = T3ServerConnector(
            readRuntime = { T3Runtime.read(home.runtimeFile) },
            isReachable = { it.isReachable() },
            launch = {
                if (!autoStart) {
                    throw T3Exception(
                        T3Exception.Reason.SERVER_NOT_RUNNING,
                        "No T3 server answers, and automatic start is off in Settings | Tools | T3 Code.",
                    )
                }
                // Store the process at once: dispose() must stop it, also during the wait.
                T3ServeProcess.start(T3Cli.require(home)).also(ownedServer::set)
            },
            sleep = {
                ProgressManager.checkCanceled()
                Thread.sleep(it.toMillis())
            },
        )
        try {
            connector.connect().runtime.also { mutableState.value = T3ServerState.Running(it.origin) }
        } catch (e: T3Exception) {
            mutableState.value = T3ServerState.Failed(e.message.orEmpty())
            throw e
        }
    }

    /** Starts the periodic check for [state]. A second call does nothing. */
    fun startMonitoring() {
        if (!monitoring.compareAndSet(false, true)) return
        coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                mutableState.value = nextServerState(
                    previous = mutableState.value,
                    remoteOrigin = T3Settings.getInstance().remoteOrigin,
                    remoteReachable = { acceptsConnections(it) },
                    reachableLocalOrigin = {
                        T3Runtime.read(T3Home.detect().runtimeFile)?.takeIf { it.isReachable() }?.origin
                    },
                )
                delay(10.seconds)
            }
        }
    }

    override fun dispose() {
        ownedServer.getAndSet(null)?.stop()
    }
}
