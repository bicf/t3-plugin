package com.bicf.t3plugin.server

import com.bicf.t3plugin.T3Exception
import com.bicf.t3plugin.T3Exception.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class T3ServerConnectorTest {

    private val runtime = T3Runtime(4242, 3773, "http://127.0.0.1:3773")

    private class FakeServer(val exitCode: Int? = null) : LaunchedServer {
        var stopped = false
        override fun exitCodeOrNull(): Int? = exitCode
        override fun stop() {
            stopped = true
        }
    }

    /** A fake machine. The clock moves only when the connector sleeps; [onPoll] runs after each sleep. */
    private class World {
        var current: T3Runtime? = null
        var reachable = false
        var nowNanos = 0L
        var polls = 0
        val launched = mutableListOf<FakeServer>()
        var nextServer: () -> FakeServer = { FakeServer() }
        var onPoll: (Int) -> Unit = {}

        fun connect(): T3Connection = T3ServerConnector(
            readRuntime = { current },
            isReachable = { reachable },
            launch = { nextServer().also(launched::add) },
            startTimeout = Duration.ofSeconds(10),
            pollInterval = Duration.ofSeconds(1),
            nanoTime = { nowNanos },
            sleep = {
                nowNanos += it.toNanos()
                polls++
                onPoll(polls)
            },
        ).connect()
    }

    @Test
    fun `uses a running server and does not start a new one`() {
        val world = World().apply {
            current = runtime
            reachable = true
        }
        val connection = world.connect()
        assertEquals(runtime, connection.runtime)
        assertNull(connection.ownedServer)
        assertTrue(world.launched.isEmpty())
    }

    @Test
    fun `starts a server when the runtime file is stale and owns it`() {
        val world = World().apply { current = runtime }
        world.onPoll = { poll -> if (poll == 2) world.reachable = true }
        val connection = world.connect()
        assertEquals(runtime, connection.runtime)
        assertSame(world.launched.single(), connection.ownedServer)
        assertFalse(world.launched.single().stopped)
    }

    @Test
    fun `waits for the other server after exit 78`() {
        val world = World().apply { nextServer = { FakeServer(exitCode = 78) } }
        world.onPoll = { poll ->
            if (poll == 3) {
                world.current = runtime
                world.reachable = true
            }
        }
        val connection = world.connect()
        assertEquals(runtime, connection.runtime)
        assertNull(connection.ownedServer)
    }

    @Test
    fun `fails at once when t3 serve stops with another exit code`() {
        val world = World().apply { nextServer = { FakeServer(exitCode = 1) } }
        val error = assertThrows(T3Exception::class.java) { world.connect() }
        assertEquals(Reason.SERVER_EXITED, error.reason)
        assertEquals(0, world.polls)
    }

    @Test
    fun `stops its server after the timeout`() {
        val world = World()
        val error = assertThrows(T3Exception::class.java) { world.connect() }
        assertEquals(Reason.SERVER_TIMEOUT, error.reason)
        assertTrue(world.launched.single().stopped)
        assertEquals(10, world.polls)
    }

    @Test
    fun `reports the other owner when it does not answer after exit 78`() {
        val world = World().apply { nextServer = { FakeServer(exitCode = 78) } }
        val error = assertThrows(T3Exception::class.java) { world.connect() }
        assertEquals(Reason.SERVER_OWNED_ELSEWHERE, error.reason)
    }
}
