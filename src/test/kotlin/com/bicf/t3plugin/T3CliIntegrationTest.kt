package com.bicf.t3plugin

import com.bicf.t3plugin.pairing.T3Sessions
import com.bicf.t3plugin.pairing.runPairingCreate
import com.bicf.t3plugin.project.T3ProjectBinder
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
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
            // The server got the home only through T3CODE_HOME: the runtime file is in the isolated home.
            assertNotNull("The test must own the server on the isolated home.", server)
            assertEquals(port, URI(connection.runtime.origin).port)

            // Step 7 twice: the second `project add` ends with ProjectAlreadyExistsError, which is a success.
            val projectDir = tmp.newFolder("demo-project").toPath().toString()
            val first = T3ProjectBinder.bind(cli, projectDir)
            val second = T3ProjectBinder.bind(cli, projectDir)
            assertEquals("/", first.path)
            assertEquals(first, second)
            assertNotNull("The server writes userdata/environment-id.", first.environmentId)

            // A real session: pairing, token exchange as the web UI does it, then revocation by label.
            val label = "JetBrains integration test (abc123)"
            val link = runPairingCreate(cli, connection.runtime.origin, label)
            assertTrue(link.url.startsWith("${connection.runtime.origin}/pair#token="))
            assertEquals(200, exchangeToken(connection.runtime.origin, link.url.substringAfter("#token=")))
            val sessions = cli.run(Duration.ofSeconds(60), "auth", "session", "list", "--json")
            assertEquals(1, T3Sessions.idsWithLabel(sessions.stdout, label).size)
            assertEquals(1, T3Sessions.revokeWithLabel(cli, label))
            val after = cli.run(Duration.ofSeconds(60), "auth", "session", "list", "--json")
            assertEquals(0, T3Sessions.idsWithLabel(after.stdout, label).size)
        } finally {
            server?.stop()
        }

        val deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos()
        while (connection.runtime.isReachable() && System.nanoTime() < deadline) Thread.sleep(200)
        assertFalse("The server still answers after stop().", connection.runtime.isReachable())
    }

    /** `POST /api/auth/browser-session`, as the `/pair` page does it. Returns the HTTP status. */
    private fun exchangeToken(origin: String, token: String): Int {
        val request = HttpRequest.newBuilder(URI("$origin/api/auth/browser-session"))
            .header("content-type", "application/json")
            .header("origin", origin)
            .POST(HttpRequest.BodyPublishers.ofString("""{"credential":"$token"}"""))
            .build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
    }
}
