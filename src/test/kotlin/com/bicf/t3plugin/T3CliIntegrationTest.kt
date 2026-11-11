package com.bicf.t3plugin

import com.bicf.t3plugin.pairing.runPairingCreate
import com.bicf.t3plugin.server.T3Cli
import com.bicf.t3plugin.server.T3Home
import com.bicf.t3plugin.server.T3Runtime
import com.bicf.t3plugin.server.T3ServeProcess
import com.bicf.t3plugin.server.T3ServerConnector
import com.intellij.openapi.util.SystemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.time.Duration

/**
 * Runs the real `t3` CLI on an isolated T3 home and a free port. The T3 home of the user does not change.
 *
 * Run: `T3_CLI_IT=1 ./gradlew test --tests com.bicf.t3plugin.T3CliIntegrationTest`
 */
class T3CliIntegrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `starts t3 serve, creates a pairing link and stops the server`() {
        assumeTrue("Set T3_CLI_IT=1 to run this test.", System.getenv("T3_CLI_IT") == "1")
        val home = T3Home(tmp.newFolder("t3home").toPath())
        val executable = T3Cli.locate(home, T3Home.userHome(), SystemInfo.isWindows) { null }
        assumeNotNull(executable)
        val cli = T3Cli(executable!!, home)
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }

        val connection = T3ServerConnector(
            readRuntime = { T3Runtime.read(home.runtimeFile) },
            isReachable = { it.isReachable() },
            launch = { T3ServeProcess.start(cli, listOf("--port", port.toString())) },
        ).connect()
        val server = connection.ownedServer
        try {
            assertNotNull("The test must own the server on the isolated home.", server)
            assertEquals(port, URI(connection.runtime.origin).port)
            val link = runPairingCreate(cli, connection.runtime.origin, "JetBrains integration test")
            assertTrue(link.url.startsWith("${connection.runtime.origin}/pair#token="))
        } finally {
            server?.stop()
        }

        val deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos()
        while (connection.runtime.isReachable() && System.nanoTime() < deadline) Thread.sleep(200)
        assertFalse("The server still answers after stop().", connection.runtime.isReachable())
    }
}
