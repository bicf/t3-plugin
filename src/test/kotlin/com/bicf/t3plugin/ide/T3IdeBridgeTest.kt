package com.bicf.t3plugin.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class T3IdeBridgeTest {

    @Test
    fun `reads a line and a column from a path suffix`() {
        assertEquals(T3FileTarget("/w/a.kt", 12, 3), T3FileTarget.parse("/w/a.kt:12:3"))
        assertEquals(T3FileTarget("/w/a.kt", 12), T3FileTarget.parse("/w/a.kt:12"))
        assertEquals(T3FileTarget("/w/a.kt", 12), T3FileTarget.parse("/w/a.kt:12-20"))
    }

    @Test
    fun `reads a line from a link fragment when the path has no suffix`() {
        assertEquals(T3FileTarget("/w/a.kt", 7, 2), T3FileTarget.parse("/w/a.kt", "#L7C2"))
        assertEquals(T3FileTarget("/w/a.kt", 7), T3FileTarget.parse("/w/a.kt", "#l7-L9"))
        assertEquals(T3FileTarget("/w/a.kt", 4), T3FileTarget.parse("/w/a.kt:4", "#L7"))
        assertEquals(T3FileTarget("/w/a.kt"), T3FileTarget.parse("/w/a.kt", "#section"))
    }

    @Test
    fun `ignores line 0 and keeps a path without a location`() {
        assertEquals(T3FileTarget("/w/a.kt"), T3FileTarget.parse("/w/a.kt:0"))
        assertEquals(T3FileTarget("/w/dir"), T3FileTarget.parse("/w/dir"))
    }

    @Test
    fun `accepts only absolute local paths`() {
        assertEquals(Path.of("/w/a.kt"), T3FileTarget("/w/x/../a.kt").toLocalPath())
        assertNull(T3FileTarget("relative/a.kt").toLocalPath())
    }

    @Test
    fun `parses each request kind`() {
        assertEquals(
            T3IdeRequest.OpenFile(1, T3FileTarget("/w/a.kt", 3)),
            T3IdeRequest.parse("""{"id":1,"kind":"file","path":"/w/a.kt","hash":"#L3"}"""),
        )
        assertEquals(
            T3IdeRequest.OpenInEditor(2, T3FileTarget("/w")),
            T3IdeRequest.parse("""{"id":2,"kind":"open","path":"/w"}"""),
        )
        assertEquals(
            T3IdeRequest.TurnDiff(3, "diff --git", 2, 3, "CHANGELOG.md"),
            T3IdeRequest.parse("""{"id":3,"kind":"diff","patch":"diff --git","fromTurn":2,"toTurn":3,"file":"CHANGELOG.md"}"""),
        )
        assertEquals(
            T3IdeRequest.TurnDiff(4, "p", null, null, null),
            T3IdeRequest.parse("""{"id":4,"kind":"diff","patch":"p","file":""}"""),
        )
        assertEquals(T3IdeRequest.Terminal(5, "/w"), T3IdeRequest.parse("""{"id":5,"kind":"terminal","cwd":"/w"}"""))
        assertEquals(T3IdeRequest.Files(6, "/w"), T3IdeRequest.parse("""{"id":6,"kind":"files","cwd":"/w"}"""))
        assertEquals(T3IdeRequest.Commit(7, "/w"), T3IdeRequest.parse("""{"id":7,"kind":"commit","cwd":"/w"}"""))
        assertEquals(T3IdeRequest.Branches(8, "/w"), T3IdeRequest.parse("""{"id":8,"kind":"branches","cwd":"/w"}"""))
        assertEquals(
            T3IdeRequest.BranchDiff(9, "/w", "origin/main"),
            T3IdeRequest.parse("""{"id":9,"kind":"branchDiff","cwd":"/w","ref":"origin/main"}"""),
        )
    }

    @Test
    fun `rejects invalid requests`() {
        assertNull(T3IdeRequest.parse("not json"))
        assertNull(T3IdeRequest.parse("""{"id":0,"kind":"file","path":"/w"}"""))
        assertNull(T3IdeRequest.parse("""{"id":"x","kind":"file","path":"/w"}"""))
        assertNull(T3IdeRequest.parse("""{"id":1,"kind":"shell","cwd":"/w"}"""))
        assertNull(T3IdeRequest.parse("""{"id":1,"kind":"terminal","cwd":" "}"""))
        assertNull(T3IdeRequest.parse("""{"id":1,"kind":"branchDiff","cwd":"/w","ref":"--output=/tmp/x"}"""))
        assertNull(T3IdeRequest.parse("""{"id":1,"kind":"branchDiff","cwd":"/w","ref":"main; rm -rf"}"""))
    }

    @Test
    fun `builds the reply script only for valid ids`() {
        assertEquals("window.__t3IdeReply&&window.__t3IdeReply(12,true);", T3IdeBridge.replyScript(12, true))
        assertTrue(runCatching { T3IdeBridge.replyScript(0, false) }.isFailure)
    }

    @Test
    fun `turns features on or off in the script`() {
        val script = T3IdeBridge.script(T3IdeBridge.Features(files = false, diff = true, terminal = false, git = true)) { "send($it)" }
        assertTrue(script.contains("var F={files:false,diff:true,terminal:false,git:true};"))
        assertTrue(script.contains("send(r)"))
        assertTrue(script.contains("'__reactFiber$'"))
        assertFalse(T3IdeBridge.Features(files = false, diff = false, terminal = false, git = false).any)
    }

    @Test
    fun `finds the clicked file in a patch`() {
        val paths = listOf("CHANGELOG.md", "src/main/T3Routes.kt", "src/test/T3Routes.kt")
        assertEquals(0, T3PatchFiles.indexOf(paths, null))
        assertEquals(1, T3PatchFiles.indexOf(paths, "src/main/T3Routes.kt"))
        assertEquals(1, T3PatchFiles.indexOf(paths, "T3Routes.kt"))
        assertEquals(0, T3PatchFiles.indexOf(paths, "missing.kt"))
        assertEquals("x/y.kt", T3PatchFiles.clean("b/x/y.kt"))
        assertNull(T3PatchFiles.clean("/dev/null"))
    }
}
