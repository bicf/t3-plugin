package com.bicf.t3plugin.pairing

import com.bicf.t3plugin.T3Exception
import com.bicf.t3plugin.T3Exception.Reason
import com.bicf.t3plugin.server.T3Cli
import com.bicf.t3plugin.server.T3Home
import com.intellij.openapi.util.SystemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.Duration

class RunPairingCreateTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val origin = "http://127.0.0.1:3773"

    /** A fake `t3` command: a POSIX shell script with [body]. */
    private fun fakeCli(body: String): T3Cli {
        assumeFalse("The fake CLI is a POSIX shell script.", SystemInfo.isWindows)
        val home = T3Home(tmp.newFolder("home").toPath())
        val script = home.dir.resolve("bin/t3")
        Files.createDirectories(script.parent)
        Files.writeString(script, "#!/bin/sh\n$body\n")
        script.toFile().setExecutable(true)
        return T3Cli(script, home, workingDirectory = tmp.root.toPath())
    }

    @Test
    fun `passes the flags and returns the link`() {
        val argsFile = tmp.root.toPath().resolve("args.txt")
        val cli = fakeCli(
            """
            printf '%s\n' "${'$'}T3CODE_HOME" "${'$'}@" > '$argsFile'
            printf '{"pairUrl":"http://127.0.0.1:3773/pair#token=fake","expiresAt":"2026-11-11T20:02:00.000Z"}'
            """.trimIndent(),
        )

        val link = runPairingCreate(cli, origin, "JetBrains IDE - demo")

        assertEquals("http://127.0.0.1:3773/pair#token=fake", link.url)
        assertEquals(
            listOf(
                cli.home.dir.toString(),
                "auth", "pairing", "create", "--json", "--ttl", "2m", "--label", "JetBrains IDE - demo", "--base-url", origin,
            ),
            Files.readAllLines(argsFile),
        )
    }

    @Test
    fun `reports the exit code and the last error line`() {
        val cli = fakeCli("echo 'first line' >&2\necho 'Error: no T3 home' >&2\nexit 3")
        val error = assertThrows(T3Exception::class.java) { runPairingCreate(cli, origin, "demo") }
        assertEquals(Reason.PAIRING_FAILED, error.reason)
        assertTrue(error.message!!.contains("exit code 3"))
        assertTrue(error.message!!.contains("Error: no T3 home"))
    }

    @Test
    fun `keeps the output out of the message when the link is missing`() {
        val cli = fakeCli("""printf '{"credential":"fake-secret-token"}'""")
        val error = assertThrows(T3Exception::class.java) { runPairingCreate(cli, origin, "demo") }
        assertEquals(Reason.PAIRING_BAD_OUTPUT, error.reason)
        assertFalse(error.message!!.contains("fake-secret-token"))
    }

    @Test
    fun `stops a command that does not end in time`() {
        val cli = fakeCli("sleep 30")
        val error = assertThrows(T3Exception::class.java) {
            runPairingCreate(cli, origin, "demo", timeout = Duration.ofMillis(500))
        }
        assertEquals(Reason.PAIRING_FAILED, error.reason)
    }
}
