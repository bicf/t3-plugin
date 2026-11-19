package com.bicf.t3plugin.toolWindow

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Runs the scripts of [T3ShellWatch] and [T3ThreadMenu] in Node (`src/test/resources/js/shell-menu-harness.js`) with a
 * fake WebSocket and a fake "Thread actions" button that calls `contextMenu.show` as T3 0.1.9 does.
 * Without `node` on the PATH, the tests are skipped.
 */
class T3ShellMenuScriptTest {

    companion object {
        private lateinit var results: Map<String, String>

        @BeforeClass
        @JvmStatic
        fun runHarness() {
            assumeTrue("Node is not on the PATH.", nodeAvailable())
            val dir = Files.createTempDirectory("t3-shell-menu").toFile()
            try {
                fun script(name: String, text: String) = File(dir, name).apply { writeText(text) }.path
                val report = { expression: String -> "send($expression)" }
                val args = listOf(
                    script("shell.js", T3ShellWatch.script(report)),
                    script("list.js", T3ThreadMenu.listScript(5, report)),
                    script("pin.js", T3ThreadMenu.chooseScript("pin")),
                    script("disabled.js", T3ThreadMenu.chooseScript("archive")),
                    script("parent.js", T3ThreadMenu.chooseScript("copy")),
                    script("child.js", T3ThreadMenu.chooseScript("copy-path")),
                    script("route.js", T3RouteWatch.script(report)),
                )
                val harness = script("harness.js", T3ShellMenuScriptTest::class.java.getResource("/js/shell-menu-harness.js")!!.readText())
                val log = File(dir, "output.txt")
                val process = ProcessBuilder(listOf("node", harness) + args).redirectErrorStream(true).redirectOutput(log).start()
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    error("The harness did not end in 30 s.")
                }
                val output = log.readText()
                check(process.exitValue() == 0) { "The harness failed: $output" }
                results = output.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            } finally {
                dir.deleteRecursively()
            }
        }

        private fun nodeAvailable(): Boolean = try {
            val p = ProcessBuilder("node", "--version").redirectErrorStream(true).start()
            p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
        } catch (e: Exception) {
            false
        }

        private fun strings(key: String): List<String> = Json.decodeFromString(results[key]!!)
    }

    @Test
    fun `the shell watch sends every message unchanged and listens once per socket`() {
        assertEquals("2", results["sentThrough"])
        assertEquals("1", results["listeners"])
    }

    @Test
    fun `the shell watch joins the parts of a long message`() {
        assertEquals("0", results["partialReports"])
    }

    @Test
    fun `the shell watch reports the snapshot, a new project and the threads with their origin`() {
        val expected = listOf(
            """shell:{"snap":true,"p":[["p1","/work/demo"]],"t":[["t1","p1",null,true,null],["t2","p1","/wt/a",false,null],["t3","p1",null,true,null]]}""",
            """shell:{"p":[["p2","/work/new"]]}""",
            """shell:{"t":[["t4","p1","/wt/b",true,"2026-11-19T17:46:11.000Z"]]}""",
            """shell:{"t":[["t5","p1","/wt/c",false,null]]}""",
            """shell:{"t":[["t6","p1","/wt/d",false,null]]}""",
        )
        assertEquals(expected, strings("reports"))
    }

    @Test
    fun `the route watch adds the project and the worktree of a saved draft`() {
        assertEquals(
            listOf("""route:2:/draft/d2 {"p":"p2","w":"/wt/b"}""", """route:2:/draft/d1 {"p":"p1","w":null}"""),
            strings("routeDraft"),
        )
        assertEquals(listOf("route:2:/env-1/t1"), strings("routeThread"))
        val route = T3RouteWatch.parseRoute(strings("routeDraft")[0].removePrefix(T3RouteWatch.ROUTE_PREFIX))
        assertEquals(T3RouteWatch.Route(2, "/draft/d2", "p2", "/wt/b"), route)
    }

    @Test
    fun `the thread menu reads the items without a menu in the page and removes its bridge`() {
        val reports = strings("listReports")
        assertEquals(1, reports.size)
        val (id, items) = T3ThreadMenu.parseReply(reports[0].removePrefix(T3ThreadMenu.PREFIX))!!
        assertEquals(5L, id)
        assertEquals(listOf("rename", "pin", "archive", "copy", "auto-settle", "delete"), items!!.map { it.id })
        assertEquals(false, items[2].enabled)
        assertEquals(true, items[2].separatorBefore)
        assertEquals(listOf("copy-path", "copy-thread-id"), items[3].children.map { it.id })
        assertEquals(true, items[4].children[0].checked)
        assertEquals(true, items[5].destructive)
        assertEquals("false", results["bridgeAfterList"])
        assertEquals("0", results["fallbackMenus"])
    }

    @Test
    fun `the thread menu runs only an enabled item without children and restores a desktop bridge`() {
        assertEquals("true", results["bridgeRestored"])
        assertEquals("""["pin","copy-path"]""", results["chosen"])
    }

    @Test
    fun `the thread menu reports null without the button`() {
        assertEquals(listOf("tmenu:5:null"), strings("noButton"))
    }
}
