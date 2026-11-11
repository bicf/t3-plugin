package com.bicf.t3plugin.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files

class T3RuntimeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // Same keys as server-runtime.json of T3 Code 0.1.9.
    private val realShape = """
        {"version":1,"pid":18157,"host":"127.0.0.1","port":3773,"origin":"http://127.0.0.1:3773",
         "startedAt":"2026-11-11T13:30:00.000Z","serverVersion":"0.1.9","managedPolicy":null,"ownerId":"owner-1"}
    """.trimIndent()

    @Test
    fun `parses the runtime file of T3 Code 0_1_9`() {
        assertEquals(T3Runtime(18157, 3773, "http://127.0.0.1:3773"), T3Runtime.parse(realShape))
    }

    @Test
    fun `removes a trailing slash from the origin`() {
        val runtime = T3Runtime.parse("""{"pid":1,"port":3773,"origin":"http://127.0.0.1:3773/"}""")
        assertEquals("http://127.0.0.1:3773", runtime?.origin)
    }

    @Test
    fun `rejects content without a valid pid, port or http origin`() {
        listOf(
            """{"port":3773,"origin":"http://127.0.0.1:3773"}""",
            """{"pid":0,"port":3773,"origin":"http://127.0.0.1:3773"}""",
            """{"pid":1,"port":70000,"origin":"http://127.0.0.1:3773"}""",
            """{"pid":1,"port":3773,"origin":"file:///etc/passwd"}""",
            """{"pid":1,"port":3773,"origin":"not a url"}""",
            """{"pid":1,"port":3773}""",
            """[1,2,3]""",
            """{"pid":""",
        ).forEach { json -> assertNull(json, T3Runtime.parse(json)) }
    }

    @Test
    fun `read returns null for a missing file`() {
        assertNull(T3Runtime.read(tmp.root.toPath().resolve("missing.json")))
    }

    @Test
    fun `read parses a file`() {
        val file = tmp.newFile("server-runtime.json").toPath()
        Files.writeString(file, realShape)
        assertEquals(3773, T3Runtime.read(file)?.port)
    }

    @Test
    fun `is reachable when the pid is alive and the port is open`() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { socket ->
            val runtime = T3Runtime(ProcessHandle.current().pid(), socket.localPort, "http://127.0.0.1:${socket.localPort}")
            assertTrue(runtime.isReachable())
        }
    }

    @Test
    fun `is not reachable when the port is closed`() {
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        assertFalse(T3Runtime(ProcessHandle.current().pid(), port, "http://127.0.0.1:$port").isReachable())
    }

    @Test
    fun `is not reachable when the pid is dead`() {
        val java = ProcessHandle.current().info().command().orElseThrow()
        val ended = ProcessBuilder(java, "-version").redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        ended.waitFor()
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { socket ->
            assertFalse(T3Runtime(ended.pid(), socket.localPort, "http://127.0.0.1:${socket.localPort}").isReachable())
        }
    }
}
