package com.bicf.t3plugin.toolWindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T3RouteWatchTest {

    private val env = "3d55f139-d277-48d0-8c5c-2546aeb0874a"

    @Test
    fun `reads a thread from the page path`() {
        assertEquals(T3Routes.ThreadRoute(env, "5ae11df5"), T3Routes.threadRoute("/$env/5ae11df5"))
        assertEquals(T3Routes.ThreadRoute(env, "mcp:1c760312"), T3Routes.threadRoute("/$env/mcp:1c760312/"))
        assertEquals(T3Routes.ThreadRoute(env, "mcp:1c760312"), T3Routes.threadRoute("/$env/mcp%3A1c760312"))
        assertNull(T3Routes.threadRoute("/$env/bad%zz"))
        assertNull(T3Routes.threadRoute("/$env/a%2Fb"))
        assertEquals(T3Routes.ThreadRoute(env, "t"), T3Routes.threadRoute("/$env/t?x=1#y"))
        assertEquals("/$env/t", T3Routes.ThreadRoute(env, "t").path)
    }

    @Test
    fun `ignores the other pages of the web UI`() {
        assertNull(T3Routes.threadRoute("/"))
        assertNull(T3Routes.threadRoute("/draft/e4f1ae27-8bef-407c-a477-64155df2d6df"))
        assertNull(T3Routes.threadRoute("/settings/about"))
        assertNull(T3Routes.threadRoute("/$env"))
        assertNull(T3Routes.threadRoute("/$env/.."))
        assertNull(T3Routes.threadRoute("/home/ivan/project/file.kt"))
    }

    @Test
    fun `goes back only to a thread path`() {
        assertTrue(T3RouteWatch.backScript("/$env/t").contains("location.assign('/$env/t')"))
        assertTrue(runCatching { T3RouteWatch.backScript("/$env/t');alert('x") }.isFailure)
    }

    @Test
    fun `reports the route and the title through the report function`() {
        val script = T3RouteWatch.script { "send($it)" }
        assertTrue(script.contains("var r='${T3RouteWatch.ROUTE_PREFIX}'+p;send(r);"))
        assertTrue(script.contains("var s='${T3RouteWatch.TITLE_PREFIX}'+t;send(s);"))
    }
}
