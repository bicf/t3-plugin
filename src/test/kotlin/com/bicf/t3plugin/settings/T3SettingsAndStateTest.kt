package com.bicf.t3plugin.settings

import com.bicf.t3plugin.actions.T3SelectionMessage
import com.bicf.t3plugin.server.T3ServerState
import com.bicf.t3plugin.server.nextServerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T3SettingsAndStateTest {

    @Test
    fun `normalizes a remote URL to its origin`() {
        assertEquals("https://devbox.example.ts.net", T3Settings.normalizeOrigin(" https://DevBox.example.ts.net/some/path "))
        assertEquals("http://10.0.0.5:3773", T3Settings.normalizeOrigin("http://10.0.0.5:3773/"))
        assertNull(T3Settings.normalizeOrigin(""))
        assertNull(T3Settings.normalizeOrigin("ftp://host"))
        assertNull(T3Settings.normalizeOrigin("devbox:3773"))
        assertNull(T3Settings.normalizeOrigin("not a url"))
    }

    @Test
    fun `server state follows the checks and keeps an error until a server answers`() {
        val failed = T3ServerState.Failed("boom")
        assertEquals(T3ServerState.Running("http://127.0.0.1:3773"), nextServerState(failed, null, { false }, { "http://127.0.0.1:3773" }))
        assertEquals(failed, nextServerState(failed, null, { false }, { null }))
        assertEquals(T3ServerState.Stopped, nextServerState(T3ServerState.Running("x"), null, { false }, { null }))
        assertEquals(T3ServerState.Remote("https://r", true), nextServerState(failed, "https://r", { true }, { null }))
    }

    @Test
    fun `selection message has question, location and fenced code`() {
        val message = T3SelectionMessage.build("Why is this slow?", "src/A.kt", 10, 12, "kt", "val a = 1\nval b = 2\nval c = 3")
        assertEquals("Why is this slow?\n\nsrc/A.kt:10-12\n\n```kt\nval a = 1\nval b = 2\nval c = 3\n```", message)
    }

    @Test
    fun `selection message uses a longer fence when the code has backticks and cuts long code`() {
        val message = T3SelectionMessage.build("", "README.md", 4, 4, "md", "```bash\nls\n```")
        assertEquals("README.md:4\n\n````md\n```bash\nls\n```\n````", message)
        val long = T3SelectionMessage.build("", "big.txt", 1, 1, "txt", "x".repeat(T3SelectionMessage.MAX_CODE_CHARS + 10))
        assertTrue(long.contains("selection cut at ${T3SelectionMessage.MAX_CODE_CHARS} characters"))
    }
}
