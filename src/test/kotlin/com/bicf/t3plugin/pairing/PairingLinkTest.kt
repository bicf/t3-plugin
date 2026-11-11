package com.bicf.t3plugin.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingLinkTest {

    private val origin = "http://127.0.0.1:3773"

    // Same keys as `t3 auth pairing create --json` of T3 Code 0.1.9. The token is fake.
    private val output = """
        {"id":"pair-1","credential":"fake-secret-token","label":"JetBrains IDE - demo","scopes":["orchestration:read"],
         "expiresAt":"2026-11-11T20:02:00.000Z","pairUrl":"http://127.0.0.1:3773/pair#token=fake-secret-token"}
    """.trimIndent()

    @Test
    fun `parses the link and the expiry`() {
        val link = PairingLink.parse(output, origin)
        assertEquals("http://127.0.0.1:3773/pair#token=fake-secret-token", link?.url)
        assertEquals("2026-11-11T20:02:00.000Z", link?.expiresAt)
    }

    @Test
    fun `accepts an origin with a trailing slash`() {
        assertNotNull(PairingLink.parse(output, "$origin/"))
    }

    @Test
    fun `rejects a link on another origin`() {
        assertNull(PairingLink.parse(output, "http://127.0.0.1:3899"))
        assertNull(PairingLink.parse(output, "http://127.0.0.1:37"))
    }

    @Test
    fun `rejects output without a link or with an empty token`() {
        assertNull(PairingLink.parse("""{"id":"pair-1"}""", origin))
        assertNull(PairingLink.parse("""{"pairUrl":"http://127.0.0.1:3773/pair#token="}""", origin))
        assertNull(PairingLink.parse("not json", origin))
    }

    @Test
    fun `toString hides the token`() {
        val text = PairingLink.parse(output, origin).toString()
        assertFalse(text.contains("fake-secret-token"))
        assertTrue(text.contains("http://127.0.0.1:3773/pair"))
    }
}
