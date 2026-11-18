package com.bicf.t3plugin.toolWindow

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Runs the script of [T3VisitGate] in Node (`src/test/resources/js/visit-gate-harness.js`) with a fake WebSocket and
 * a fake `Storage`, and checks the behavior. Without `node` on the PATH, the tests are skipped.
 */
class T3VisitGateScriptTest {

    companion object {
        private lateinit var results: Map<String, String>

        @BeforeClass
        @JvmStatic
        fun runHarness() {
            assumeTrue("Node is not on the PATH.", nodeAvailable())
            val dir = Files.createTempDirectory("t3-visit-gate").toFile()
            try {
                val hidden = File(dir, "hidden.js").apply { writeText(T3VisitGate.script(false)) }
                val shown = File(dir, "shown.js").apply { writeText(T3VisitGate.script(true)) }
                val harness = File(dir, "harness.js").apply {
                    writeText(T3VisitGateScriptTest::class.java.getResource("/js/visit-gate-harness.js")!!.readText())
                }
                val log = File(dir, "output.txt")
                val process = ProcessBuilder("node", harness.path, hidden.path, shown.path)
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
    fun `a hidden page holds the visits and sends the other requests`() {
        assertEquals("[9]", results["hiddenSent"])
    }

    @Test
    fun `a newer visit of the same thread gets the old one answered after the last part of a message`() {
        assertEquals("0", results["exitDuringParts"])
        assertEquals(
            "\"{\\\"_tag\\\":\\\"Exit\\\",\\\"requestId\\\":5,\\\"exit\\\":{\\\"_tag\\\":\\\"Success\\\",\\\"value\\\":{\\\"sequence\\\":0}}}\"",
            results["exitAfterParts"],
        )
    }

    @Test
    fun `a hidden page keeps the stored visit times and saves the other fields`() {
        assertEquals(
            "{\"projectOrder\":[\"b\"],\"threadLastVisitedAtById\":{\"k1\":\"2026-11-18T09:00:00Z\"}}",
            results["hiddenStored"],
        )
        assertEquals("\"{bad json\"", results["badJsonStored"])
    }

    @Test
    fun `a hidden save keeps the stored times when the saved time is older or the thread is missing`() {
        assertEquals(
            "{\"k1\":\"2026-11-18T09:00:00Z\",\"k3\":\"2026-11-18T08:00:00Z\"}",
            results["hiddenOlderStored"],
        )
    }

    @Test
    fun `the page sends each held visit once and saves the held visit times when it shows`() {
        assertEquals("[9,7,6]", results["shownSent"])
        assertEquals(
            "{\"k1\":\"2026-11-18T10:00:00Z\",\"k2\":\"2026-11-18T10:00:00Z\"}",
            results["shownStored"],
        )
        assertEquals("3", results["secondShowSent"])
        assertEquals("[9,7,6,8]", results["visibleSent"])
    }

    @Test
    fun `a page hidden again holds the visits and sends them to the last open socket`() {
        assertEquals("{\"closed\":4,\"open\":[11,10]}", results["rehideSent"])
        assertEquals("[11,10]", results["droppedSent"])
    }

    @Test
    fun `the gate keeps the held visit times when it cannot save them and saves them at the next show`() {
        assertEquals("null", results["missingStateStored"])
        assertEquals(
            "{\"projectOrder\":[\"c\"],\"threadLastVisitedAtById\":" +
                "{\"k1\":\"2026-11-18T10:00:00Z\",\"k5\":\"2026-11-18T10:05:00Z\"}}",
            results["retryStored"],
        )
    }

    @Test
    fun `the log names each event and no content`() {
        assertEquals(
            "[\"held\",\"held\",\"superseded\",\"held\",\"local held\",\"local error\",\"sent\",\"sent\",\"local saved\"," +
                "\"held\",\"local held\",\"sent\",\"local error\",\"held\",\"dropped\",\"local saved\"]",
            results["log"],
        )
    }
}
