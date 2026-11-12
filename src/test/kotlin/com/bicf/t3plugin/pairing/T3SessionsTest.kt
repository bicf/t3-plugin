package com.bicf.t3plugin.pairing

import com.bicf.t3plugin.server.FakeCli
import com.intellij.openapi.util.SystemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class T3SessionsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val label = "JetBrains IntelliJ IDEA (a1b2c3)"

    // Same keys as `t3 auth session list --json` of T3 Code 0.1.9.
    private val sessions = """
        [{"sessionId":"s-1","method":"browser-session-cookie","client":{"label":"$label","ipAddress":"127.0.0.1","deviceType":"desktop"}},
         {"sessionId":"s-2","method":"bearer-access-token","client":{"label":"T3 desktop","ipAddress":"127.0.0.1","deviceType":"desktop"}},
         {"sessionId":"s-3","method":"browser-session-cookie","client":{"label":"$label","ipAddress":"127.0.0.1","deviceType":"desktop"}}]
    """.trimIndent()

    @Test
    fun `finds only the sessions with the exact label`() {
        assertEquals(listOf("s-1", "s-3"), T3Sessions.idsWithLabel(sessions, label))
        assertEquals(emptyList<String>(), T3Sessions.idsWithLabel(sessions, "JetBrains IntelliJ IDEA"))
        assertEquals(emptyList<String>(), T3Sessions.idsWithLabel("not json", label))
    }

    @Test
    fun `revokes each session with the label`() {
        assumeFalse("The fake CLI is a POSIX shell script.", SystemInfo.isWindows)
        val revoked = tmp.root.toPath().resolve("revoked.txt")
        val cli = FakeCli.create(
            tmp.root.toPath(),
            """
            case "${'$'}1 ${'$'}2 ${'$'}3" in
              "auth session list") printf '%s' '${sessions.replace("\n", " ")}' ;;
              "auth session revoke") echo "${'$'}4" >> '$revoked' ;;
            esac
            """.trimIndent(),
        )
        assertEquals(2, T3Sessions.revokeWithLabel(cli, label))
        assertEquals(listOf("s-1", "s-3"), Files.readAllLines(revoked))
    }

    @Test
    fun `label names the product and the system directory`() {
        val a = T3PairingService.labelFor("IntelliJ IDEA", "/home/u/.cache/JetBrains/IntelliJIdea2026.2")
        val b = T3PairingService.labelFor("IntelliJ IDEA", "/home/u/.cache/JetBrains/IntelliJIdea2026.3")
        assertTrue(Regex("JetBrains IntelliJ IDEA \\([0-9a-f]{6}\\)").matches(a))
        assertNotEquals(a, b)
        assertEquals(a, T3PairingService.labelFor("IntelliJ IDEA", "/home/u/.cache/JetBrains/IntelliJIdea2026.2"))
    }

    @Test
    fun `accepts a pasted link only on the remote origin`() {
        val origin = "https://devbox.example.ts.net"
        assertNotNull(PairingLink.fromPasted("  https://devbox.example.ts.net/pair#token=abc123 \n", origin))
        assertNull(PairingLink.fromPasted("https://devbox.example.ts.net/pair#token=", origin))
        assertNull(PairingLink.fromPasted("https://devbox.example.ts.net/pair#token=abc 123", origin))
        assertNull(PairingLink.fromPasted("https://evil.example.com/pair#token=abc123", origin))
        assertNull(PairingLink.fromPasted(null, origin))
    }
}
