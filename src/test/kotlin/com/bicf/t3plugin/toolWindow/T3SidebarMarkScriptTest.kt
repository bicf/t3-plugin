package com.bicf.t3plugin.toolWindow

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Runs the script of [T3SidebarMark] in Node (`src/test/resources/js/sidebar-mark-harness.js`) with a fake sidebar of
 * 3 rows (`env:a`, `env2:a`, `env:b`), and checks the behavior. Without `node` on the PATH, the tests are skipped.
 */
class T3SidebarMarkScriptTest {

    companion object {
        private lateinit var results: Map<String, String>

        @BeforeClass
        @JvmStatic
        fun runHarness() {
            assumeTrue("Node is not on the PATH.", nodeAvailable())
            val dir = Files.createTempDirectory("t3-sidebar-mark").toFile()
            try {
                fun mark(name: String, route: T3Routes.ThreadRoute?) =
                    File(dir, name).apply { writeText(T3SidebarMark.script(route)) }
                val a = mark("mark-a.js", T3Routes.ThreadRoute("env", "a"))
                val b = mark("mark-b.js", T3Routes.ThreadRoute("env", "b"))
                val none = mark("mark-null.js", null)
                val source = T3SidebarMarkScriptTest::class.java.getResource("/js/sidebar-mark-harness.js")!!
                val harness = File(dir, "harness.js").apply { writeText(source.readText()) }
                val log = File(dir, "output.txt")
                val process = ProcessBuilder("node", harness.path, a.path, b.path, none.path)
                    .redirectErrorStream(true).redirectOutput(log).start()
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    error("The harness did not end in 30 s.")
                }
                val output = log.readText()
                check(process.exitValue() == 0) { "The harness failed: $output" }
                results = output.lines().filter { '=' in it }
                    .associate { it.substringBefore('=') to it.substringAfter('=') }
            } finally {
                dir.deleteRecursively()
            }
        }

        private fun nodeAvailable(): Boolean = try {
            val p = ProcessBuilder("node", "--version").redirectErrorStream(true).start()
            val ended = p.waitFor(10, TimeUnit.SECONDS)
            if (!ended) p.destroyForcibly()
            ended && p.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    @Test
    fun `the mark goes only to the row with the full key and scrolls it into view`() {
        assertEquals("[\"env:a\"]", results["firstMarked"])
        assertEquals("1", results["firstScrolls"])
    }

    @Test
    fun `the mark moves to another thread and scrolls once per move`() {
        assertEquals("[\"env:b\"]", results["movedMarked"])
        assertEquals("2", results["movedScrolls"])
        assertEquals("2", results["sameScrolls"])
    }

    @Test
    fun `a row that the sidebar creates again gets the mark again in one frame`() {
        assertEquals("[\"env:b\"]", results["remarked"])
        assertEquals("1", results["framesQueued"])
    }

    @Test
    fun `no thread removes the mark and the style is added once`() {
        assertEquals("[]", results["clearedMarked"])
        assertEquals("1", results["styles"])
    }
}
