package com.bicf.t3plugin.toolWindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Checks the scripts of [T3Layout]: each mode gives a script that Node parses (`node --check`), and each mode reports
 * the messages of its rules. Without `node` on the PATH, the syntax test is skipped.
 */
class T3LayoutScriptTest {

    private fun script(mode: T3Layout.Mode) = T3Layout.script(mode) { "report($it)" }

    @Test
    fun `the script of each mode is valid JavaScript`() {
        assumeTrue("Node is not on the PATH.", nodeAvailable())
        val dir = Files.createTempDirectory("t3-layout").toFile()
        try {
            T3Layout.Mode.entries.forEach { mode ->
                val file = File(dir, "${mode.name}.js").apply { writeText(script(mode)) }
                val process = ProcessBuilder("node", "--check", file.path).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                assertTrue("node did not end", process.waitFor(30, TimeUnit.SECONDS))
                assertEquals("$mode: $output", 0, process.exitValue())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an editor tab sends the toggles of the right panel and of the thread details`() {
        val script = script(T3Layout.Mode.CONVERSATION)
        assertTrue(script.contains("'${T3Layout.PREFIX}${T3Layout.PANEL_TOGGLE}'"))
        assertTrue(script.contains("'${T3Layout.PREFIX}${T3Layout.PANEL_OPENED}'"))
        assertTrue(script.contains("'${T3Layout.PREFIX}${T3Layout.DETAILS_TOGGLE}'"))
        assertTrue(script.contains("button[aria-label=\"Toggle thread details panel\"]"))
    }

    @Test
    fun `the details tool window shows only the card and sends the right panel to the plugin`() {
        val script = script(T3Layout.Mode.DETAILS)
        assertTrue(script.contains("[data-thread-details-panel]{visibility:visible!important;position:fixed!important"))
        assertTrue(script.contains("'${T3Layout.PREFIX}${T3Layout.PANEL_OPENED}'"))
        assertTrue(!script.contains(T3Layout.DETAILS_TOGGLE))
    }

    private fun nodeAvailable(): Boolean = try {
        val p = ProcessBuilder("node", "--version").redirectErrorStream(true).start()
        p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
    } catch (e: Exception) {
        false
    }
}
