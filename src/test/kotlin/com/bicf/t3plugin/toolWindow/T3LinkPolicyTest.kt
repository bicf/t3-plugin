package com.bicf.t3plugin.toolWindow

import com.bicf.t3plugin.toolWindow.T3LinkPolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class T3LinkPolicyTest {

    private val origin = "http://127.0.0.1:3773"

    @Test
    fun `keeps pages of the server origin in the panel`() {
        assertEquals(Action.ALLOW, T3LinkPolicy.forNavigation(origin, "http://127.0.0.1:3773/"))
        assertEquals(Action.ALLOW, T3LinkPolicy.forNavigation(origin, "http://127.0.0.1:3773/env/thread?x=1#y"))
        assertEquals(Action.ALLOW, T3LinkPolicy.forNavigation(origin, "HTTP://127.0.0.1:3773/pair#token=abc"))
    }

    @Test
    fun `opens other http origins in the system browser`() {
        assertEquals(Action.OPEN_EXTERNALLY, T3LinkPolicy.forNavigation(origin, "https://github.com/bicf/t3-plugin"))
        assertEquals(Action.OPEN_EXTERNALLY, T3LinkPolicy.forNavigation(origin, "http://127.0.0.1:3899/"))
        assertEquals(Action.OPEN_EXTERNALLY, T3LinkPolicy.forNavigation(origin, "https://127.0.0.1:3773/"))
        assertEquals(Action.OPEN_EXTERNALLY, T3LinkPolicy.forNavigation(origin, "mailto:someone@example.com"))
    }

    @Test
    fun `treats the default port as equal to no port`() {
        assertEquals(Action.ALLOW, T3LinkPolicy.forNavigation("https://t3.example.net", "https://t3.example.net:443/x"))
        assertEquals(Action.ALLOW, T3LinkPolicy.forNavigation("http://t3.example.net:80", "http://t3.example.net/x"))
    }

    @Test
    fun `keeps internal schemes and blocks files and invalid URLs`() {
        assertEquals(Action.ALLOW, T3LinkPolicy.forNavigation(origin, "about:blank"))
        assertEquals(Action.ALLOW, T3LinkPolicy.forNavigation(origin, "data:text/html,hi"))
        assertEquals(Action.BLOCK, T3LinkPolicy.forNavigation(origin, "file:///etc/passwd"))
        assertEquals(Action.BLOCK, T3LinkPolicy.forNavigation(origin, "not a url"))
    }

    @Test
    fun `allows everything before the origin is known`() {
        assertEquals(Action.ALLOW, T3LinkPolicy.forNavigation(null, "https://github.com/"))
    }

    @Test
    fun `loads same-origin popups in place and opens others outside`() {
        assertEquals(Action.LOAD_IN_PLACE, T3LinkPolicy.forPopup(origin, "http://127.0.0.1:3773/settings"))
        assertEquals(Action.OPEN_EXTERNALLY, T3LinkPolicy.forPopup(origin, "https://docs.example.com/"))
        assertEquals(Action.BLOCK, T3LinkPolicy.forPopup(origin, "about:blank"))
    }
}
