package com.bicf.t3plugin.toolWindow

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Runs the script of [T3ScrollToEnd] in Node (`src/test/resources/js/scroll-to-end-harness.js`) with a fake DOM:
 * a timeline of 3000 px with a height of 500 px, and a composer that covers its bottom 80 px.
 * Without `node` on the PATH, the tests are skipped.
 */
class T3ScrollToEndScriptTest {

    companion object {
        private lateinit var results: Map<String, String>

        @BeforeClass
        @JvmStatic
        fun runHarness() {
            assumeTrue("Node is not on the PATH.", nodeAvailable())
            val dir = Files.createTempDirectory("t3-scroll-to-end").toFile()
            try {
                val script = File(dir, "script.js").apply { writeText(T3ScrollToEnd.script()) }
                val harness = File(dir, "harness.js").apply {
                    writeText(T3ScrollToEndScriptTest::class.java.getResource("/js/scroll-to-end-harness.js")!!.readText())
                }
                val log = File(dir, "output.txt")
                val process = ProcessBuilder("node", harness.path, script.path)
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
            p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    @Test
    fun `a timeline at its end shows no button`() {
        assertEquals("\"absent\"", results["atEnd"])
    }

    @Test
    fun `a timeline scrolled up shows the button above the composer`() {
        assertEquals("\"inline-flex\"", results["scrolledUp"])
        assertEquals("\"500px\"", results["left"])
        assertEquals("\"288px\"", results["bottom"])
        assertEquals("\"Scroll to end\"", results["label"])
    }

    @Test
    fun `the button hides while T3 shows its own button`() {
        assertEquals("\"none\"", results["t3Shown"])
        assertEquals("\"inline-flex\"", results["t3Hidden"])
    }

    @Test
    fun `a click scrolls to the end without taking the focus`() {
        assertEquals("true", results["pointerdownPrevented"])
        assertEquals("3000", results["afterClickScrollTop"])
        assertEquals("\"none\"", results["afterClick"])
    }

    @Test
    fun `a hidden timeline shows no button`() {
        assertEquals("\"none\"", results["hiddenTimeline"])
    }

    @Test
    fun `a second run of the script adds no second button`() {
        assertEquals("1", results["buttons"])
    }
}
