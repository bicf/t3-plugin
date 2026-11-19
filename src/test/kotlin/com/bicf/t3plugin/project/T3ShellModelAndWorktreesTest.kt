package com.bicf.t3plugin.project

import com.bicf.t3plugin.server.FakeCli
import com.bicf.t3plugin.server.T3Home
import com.intellij.openapi.util.SystemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class T3ShellModelAndWorktreesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun update(json: String) = T3ShellModel.parse(json)!!

    private val snapshot = update(
        """{"snap":true,"p":[["p1","/work/demo"]],"t":[["t1","p1",null,true],["t2","p1","/wt/a",true]]}""",
    )

    @Test
    fun `reports nothing before the first snapshot and for a snapshot`() {
        val model = T3ShellModel()
        assertEquals(emptyList<T3ShellModel.Change>(), model.apply(update("""{"p":[["p0","/work/early"]]}""")))
        assertEquals(emptyList<T3ShellModel.Change>(), model.apply(snapshot))
        assertEquals("/work/early", model.projectRoot("p0"))
        assertEquals(emptyList<T3ShellModel.Change>(), model.apply(update("""{"snap":true,"p":[["p9","/work/x"]]}""")))
    }

    @Test
    fun `reports a new project once`() {
        val model = T3ShellModel().apply { apply(snapshot) }
        val added = update("""{"p":[["p2","/work/new"]]}""")
        assertEquals(listOf(T3ShellModel.Change.ProjectAdded(T3ShellModel.Project("p2", "/work/new"))), model.apply(added))
        assertEquals(emptyList<T3ShellModel.Change>(), model.apply(added))
        assertEquals(emptyList<T3ShellModel.Change>(), model.apply(update("""{"p":[["p1","/work/renamed"]]}""")))
    }

    @Test
    fun `reports a new worktree of a user thread once`() {
        val model = T3ShellModel().apply { apply(snapshot) }
        val worktree = update("""{"t":[["t1","p1","/wt/b",true]]}""")
        val thread = T3ShellModel.Thread("t1", "p1", "/wt/b", true)
        assertEquals(listOf(T3ShellModel.Change.WorktreeCreated(thread, "/work/demo")), model.apply(worktree))
        assertEquals(emptyList<T3ShellModel.Change>(), model.apply(worktree))
    }

    @Test
    fun `reports an unknown thread only when it was created after the first snapshot`() {
        val start = Instant.parse("2026-11-19T17:50:00Z")
        val model = T3ShellModel { start }.apply { apply(snapshot) }
        // An older settled thread is not in the snapshot: its first update is not a new worktree.
        val settled = update("""{"t":[["t7","p1","/wt/old",true,"2026-11-19T17:10:05.589Z"]]}""")
        assertEquals(emptyList<T3ShellModel.Change>(), model.apply(settled))
        assertEquals(emptyList<T3ShellModel.Change>(), model.apply(update("""{"t":[["t8","p1","/wt/x",true]]}""")))
        assertEquals(emptyList<T3ShellModel.Change>(), model.apply(update("""{"t":[["t9","p1","/wt/y",true,"not a time"]]}""")))
        // A known thread that gets another worktree is reported.
        assertEquals(1, model.apply(update("""{"t":[["t7","p1","/wt/new",true,"2026-11-19T17:10:05.589Z"]]}""")).size)
        // A new thread is reported.
        val created = Instant.parse("2026-11-19T17:51:00Z")
        val newThread = update("""{"t":[["t10","p1","/wt/c",true,"2026-11-19T17:51:00Z"]]}""")
        val thread = T3ShellModel.Thread("t10", "p1", "/wt/c", true, created)
        assertEquals(listOf(T3ShellModel.Change.WorktreeCreated(thread, "/work/demo")), model.apply(newThread))
    }

    @Test
    fun `ignores the worktrees of agent threads, threads without a worktree and known worktrees`() {
        val model = T3ShellModel().apply { apply(snapshot) }
        assertEquals(emptyList<T3ShellModel.Change>(), model.apply(update("""{"t":[["t4","p1","/wt/d",false]]}""")))
        assertEquals(emptyList<T3ShellModel.Change>(), model.apply(update("""{"t":[["t5","p1",null,true]]}""")))
        assertEquals(emptyList<T3ShellModel.Change>(), model.apply(update("""{"t":[["t2","p1","/wt/a",true]]}""")))
    }

    @Test
    fun `finds the threads and the thread of a worktree`() {
        val model = T3ShellModel().apply { apply(snapshot) }
        assertEquals("p1", model.thread("t2")?.projectId)
        assertEquals("t2", model.threadInWorktree("/wt/a") { a, b -> a == b }?.id)
        assertNull(model.threadInWorktree("/wt/none") { a, b -> a == b })
    }

    @Test
    fun `parses only valid records`() {
        val parsed = update("""{"p":[["p1",""],["p2"],[1,"/x"],["p3","/ok"]],"t":[["t1"],["t2","p1",5,true],["t3","p1","",1]]}""")
        assertEquals(listOf(T3ShellModel.Project("p3", "/ok")), parsed.projects)
        assertEquals(listOf(T3ShellModel.Thread("t2", "p1", null, true), T3ShellModel.Thread("t3", "p1", null, false)), parsed.threads)
        assertNull(T3ShellModel.parse("not json"))
        assertNull(T3ShellModel.parse("[]"))
    }

    @Test
    fun `reads the main folder of a T3 worktree`() {
        assertEquals(Path.of("/work/demo"), T3Worktrees.mainFolderOf("gitdir: /work/demo/.git/worktrees/t3-1a2b\n"))
        assertNull(T3Worktrees.mainFolderOf("gitdir: /work/demo/.git"))
        assertNull(T3Worktrees.mainFolderOf("gitdir: /work/demo/.git/modules/sub"))
        assertNull(T3Worktrees.mainFolderOf("ref: refs/heads/main"))
    }

    @Test
    fun `a folder is a T3 worktree only under the T3 home`() {
        val home = T3Home(tmp.root.toPath().resolve("home"))
        fun worktree(folder: Path): Path = folder.also {
            Files.createDirectories(it)
            Files.writeString(it.resolve(".git"), "gitdir: /work/demo/.git/worktrees/${it.fileName}\n")
        }
        val inside = worktree(home.worktreesDir.resolve("demo").resolve("t3-1a2b"))
        val outside = worktree(tmp.root.toPath().resolve("elsewhere"))
        assertEquals(Path.of("/work/demo"), T3Worktrees.mainFolder(inside, home))
        assertNull(T3Worktrees.mainFolder(outside, home))
        assertNull(T3Worktrees.mainFolder(home.worktreesDir.resolve("missing"), home))
        assertEquals("/work/demo", T3Worktrees.cliFolder(inside.toString(), home))
        assertEquals(outside.toString(), T3Worktrees.cliFolder(outside.toString(), home))
    }

    @Test
    fun `the binding of a T3 worktree adds no project and opens no thread`() {
        assumeFalse("The fake CLI is a POSIX shell script.", SystemInfo.isWindows)
        val calls = tmp.root.toPath().resolve("calls.txt")
        val cli = FakeCli.create(tmp.root.toPath(), "echo \"${'$'}*\" >> '$calls'; printf '[{\"threadId\":\"newest\"}]'")
        val folder = cli.home.worktreesDir.resolve("demo").resolve("t3-1a2b")
        Files.createDirectories(folder)
        Files.writeString(folder.resolve(".git"), "gitdir: /work/demo/.git/worktrees/t3-1a2b\n")
        assertEquals(T3ProjectBinder.Binding("/", null), T3ProjectBinder.bind(cli, folder.toString()))
        assertTrue(Files.notExists(calls))
    }
}
