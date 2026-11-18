package com.bicf.t3plugin.toolWindow

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Runs the script of [T3PointerMedia] in Node (`src/test/resources/js/pointer-media-harness.js`) with a fake CSSOM,
 * and checks the media texts after the script. Without `node` on the PATH, the tests are skipped.
 */
class T3PointerMediaScriptTest {

    companion object {
        private lateinit var results: Map<String, String>

        @BeforeClass
        @JvmStatic
        fun runHarness() {
            assumeTrue("Node is not on the PATH.", nodeAvailable())
            val dir = Files.createTempDirectory("t3-pointer-media").toFile()
            try {
                val script = File(dir, "script.js").apply { writeText(T3PointerMedia.script()) }
                val harness = File(dir, "harness.js").apply {
                    writeText(T3PointerMediaScriptTest::class.java.getResource("/js/pointer-media-harness.js")!!.readText())
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
            ProcessBuilder("node", "--version").redirectErrorStream(true).start().waitFor(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            false
        }
    }

    @Test
    fun `the media queries of a mouse always apply`() {
        assertEquals("all", results["hover"])
        assertEquals("all", results["anyHover"])
        assertEquals("all", results["pointerFine"])
    }

    @Test
    fun `the media queries of a device without a mouse never apply`() {
        assertEquals("not all", results["hoverNone"])
        assertEquals("not all", results["pointerNone"])
    }

    @Test
    fun `the other media queries stay as T3 wrote them`() {
        assertEquals("(pointer: coarse)", results["pointerCoarse"])
        assertEquals("(hover: hover) and (min-width: 40rem)", results["combined"])
        assertEquals("(min-width: 48rem)", results["width"])
    }

    @Test
    fun `media rules in layers and in other media rules change too`() {
        assertEquals("all", results["layered"])
        assertEquals("(min-width: 48rem)|all", results["nested"])
    }

    @Test
    fun `a style sheet of another origin does not stop the script`() {
        assertEquals("all", results["hover"])
    }

    @Test
    fun `style sheets added later change too`() {
        assertEquals("true", results["observesSubtree"])
        assertEquals("all", results["addedSheet"])
        assertEquals("(hover: hover)", results["imageLoad"])
        assertEquals("all", results["linkLoad"])
    }

    @Test
    fun `matchMedia answers as for a mouse`() {
        assertEquals("true", results["jsHover"])
        assertEquals("true", results["jsHoverCompact"])
        assertEquals("true", results["jsPointerFine"])
        assertEquals("false", results["jsHoverNone"])
        assertEquals("false", results["jsPointerNone"])
    }

    @Test
    fun `matchMedia sends the other media queries to the browser`() {
        assertEquals("false", results["jsPointerCoarse"])
        assertEquals("true", results["jsWidth"])
        assertEquals("false", results["jsCombined"])
    }

    @Test
    fun `the script installs once and does not scan a sheet twice`() {
        assertEquals("(hover: hover)", results["secondRunSkipsSeenSheets"])
    }
}
