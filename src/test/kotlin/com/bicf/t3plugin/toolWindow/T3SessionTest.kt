package com.bicf.t3plugin.toolWindow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T3SessionTest {

    @Test
    fun `reads an authenticated session`() {
        assertTrue(T3Session.isAuthenticated("""{"authenticated":true,"auth":{"policy":"desktop-managed-local"}}"""))
    }

    @Test
    fun `reads the answer of T3 Code 0_1_9 without a session`() {
        val body = """
            {"authenticated":false,"auth":{"policy":"desktop-managed-local","bootstrapMethods":["desktop-bootstrap"],
             "sessionMethods":["browser-session-cookie","bearer-access-token","dpop-access-token"],
             "sessionCookieName":"t3_session_3773","serverUpdateScope":"environment:maintain"}}
        """.trimIndent()
        assertFalse(T3Session.isAuthenticated(body))
    }

    @Test
    fun `treats other content as no session`() {
        listOf("", "<html></html>", """{"authenticated":"yes"}""", """[true]""", """{}""").forEach {
            assertFalse(it, T3Session.isAuthenticated(it))
        }
    }
}
