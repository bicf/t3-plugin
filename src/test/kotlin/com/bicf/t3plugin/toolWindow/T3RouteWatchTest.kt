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
    fun `goes back only to a tab page`() {
        assertTrue(T3RouteWatch.backScript("/$env/t").contains("location.assign('/$env/t')"))
        assertTrue(T3RouteWatch.backScript("/draft/d1").contains("location.assign('/draft/d1')"))
        assertTrue(runCatching { T3RouteWatch.backScript("/$env/t');alert('x") }.isFailure)
        assertTrue(runCatching { T3RouteWatch.backScript(T3Routes.NAVIGATOR_PATH) }.isFailure)
    }

    @Test
    fun `goes home only to a safe path`() {
        val script = T3RouteWatch.homeScript(T3Routes.NAVIGATOR_PATH)
        assertTrue(script.contains("history.pushState({},'','${T3Routes.NAVIGATOR_PATH}')"))
        assertTrue(script.contains("new PopStateEvent('popstate'"))
        assertTrue(runCatching { T3RouteWatch.homeScript("/x');alert('y") }.isFailure)
        assertTrue(runCatching { T3RouteWatch.homeScript("/a/../b") }.isFailure)
    }

    @Test
    fun `finds the editor tab of a page`() {
        val thread = T3Routes.tabPage("/$env/mcp%3A1c76")
        assertEquals(T3Routes.TabPage(T3Routes.PageKind.THREAD, "thread:$env/mcp:1c76"), thread)
        assertEquals(thread, T3Routes.tabPage("/$env/mcp:1c76"))
        assertEquals(T3Routes.TabPage(T3Routes.PageKind.DRAFT, "draft:c319"), T3Routes.tabPage("/draft/c319"))
        val settings = T3Routes.tabPage("/settings/general")
        assertEquals(T3Routes.TabPage(T3Routes.PageKind.PAGE, "page:settings"), settings)
        assertEquals(settings, T3Routes.tabPage("/settings/appearance?x=1"))
        assertTrue(settings!!.isSettings)
        assertEquals("page:pull-requests", T3Routes.tabPage("/pull-requests")?.key)
        assertEquals("page:usage", T3Routes.tabPage("/usage")?.key)
    }

    @Test
    fun `opens no tab for the other pages`() {
        listOf(
            "/", "", T3Routes.NAVIGATOR_PATH, "/pair", "/api/auth/session", "/draft", "/$env",
            "/home/ivan/project/file.kt", "/settings/../x", "/settings/a b", "/draft/x');alert('y",
        ).forEach { assertNull(it, T3Routes.tabPage(it)) }
    }

    @Test
    fun `the layout script hides the sidebars of a conversation and reports the right panel`() {
        val conversation = T3Layout.script(T3Layout.Mode.CONVERSATION) { "send($it)" }
        assertTrue(conversation.contains("[data-slot=sidebar]{display:none!important}"))
        assertTrue(conversation.contains("[aria-label^=\"Workspace panel\"]{display:none!important}"))
        assertTrue(conversation.contains("var m='${T3Layout.PREFIX}${T3Layout.PANEL_TOGGLE}';send(m);"))
        assertTrue(conversation.contains("var m='${T3Layout.PREFIX}${T3Layout.PANEL_OPENED}';send(m);"))
        assertTrue(!T3Layout.earlyScript(T3Layout.Mode.CONVERSATION).contains("matchMedia"))
    }

    @Test
    fun `the layout script of the tool windows answers the narrow media queries of T3`() {
        val navigator = T3Layout.earlyScript(T3Layout.Mode.NAVIGATOR)
        assertTrue(navigator.contains("['(max-width: 767px)','(max-width: 980px)']"))
        assertTrue(navigator.contains("[data-slot=sidebar-inset],main{display:none!important}"))
        val panel = T3Layout.script(T3Layout.Mode.PANEL) { "send($it)" }
        assertTrue(panel.contains("matchMedia"))
        assertTrue(panel.contains("div:has(> [aria-label^=\"Workspace panel\"])"))
        assertTrue(!panel.contains("send("))
    }

    @Test
    fun `reports the route and the title through the report function`() {
        val script = T3RouteWatch.script { "send($it)" }
        assertTrue(script.contains("var r='${T3RouteWatch.ROUTE_PREFIX}'+p;send(r);"))
        assertTrue(script.contains("var s='${T3RouteWatch.TITLE_PREFIX}'+t;send(s);"))
    }
}
