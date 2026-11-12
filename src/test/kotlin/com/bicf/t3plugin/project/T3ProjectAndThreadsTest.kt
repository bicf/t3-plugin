package com.bicf.t3plugin.project

import com.bicf.t3plugin.T3Exception
import com.bicf.t3plugin.T3Exception.Reason
import com.bicf.t3plugin.server.FakeCli
import com.intellij.openapi.util.SystemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class T3ProjectAndThreadsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val env = "3d55f139-d277-48d0-8c5c-2546aeb0874a"

    @Before
    fun posixOnly() = assumeFalse("The fake CLI is a POSIX shell script.", SystemInfo.isWindows)

    /** A fake CLI that answers `project add` with [addExit] and [addStderr], and `thread list` with [threads]. */
    private fun projectCli(addExit: Int, addStderr: String = "", threads: String = "[]", writeEnvironment: Boolean = true) =
        FakeCli.create(
            tmp.root.toPath(),
            """
            case "${'$'}1 ${'$'}2" in
              "project add") echo '$addStderr' >&2; exit $addExit ;;
              "thread list") printf '%s' '$threads' ;;
            esac
            """.trimIndent(),
        ).also { cli ->
            if (writeEnvironment) {
                Files.createDirectories(cli.home.environmentIdFile.parent)
                Files.writeString(cli.home.environmentIdFile, "$env\n")
            }
        }

    @Test
    fun `opens the newest thread of a new project`() {
        val cli = projectCli(addExit = 0, threads = """[{"threadId":"newest"},{"threadId":"older"}]""")
        assertEquals(T3ProjectBinder.Binding("/$env/newest", env), T3ProjectBinder.bind(cli, "/work/demo"))
    }

    @Test
    fun `treats ProjectAlreadyExistsError as success`() {
        val cli = projectCli(addExit = 1, addStderr = "ProjectAlreadyExistsError: An active project already exists for /work/demo.")
        assertEquals(T3ProjectBinder.Binding("/", env), T3ProjectBinder.bind(cli, "/work/demo"))
    }

    @Test
    fun `fails on another project add error`() {
        val cli = projectCli(addExit = 1, addStderr = "PermissionDenied")
        val error = assertThrows(T3Exception::class.java) { T3ProjectBinder.bind(cli, "/work/demo") }
        assertEquals(Reason.PROJECT_ADD_FAILED, error.reason)
    }

    @Test
    fun `opens the root without an environment id`() {
        val cli = projectCli(addExit = 0, threads = """[{"threadId":"newest"}]""", writeEnvironment = false)
        assertEquals(T3ProjectBinder.Binding("/", null), T3ProjectBinder.bind(cli, "/work/demo"))
    }

    @Test
    fun `reads only a safe newest thread id`() {
        assertEquals("a1", T3ProjectBinder.newestThreadId("""[{"threadId":"a1"}]"""))
        assertNull(T3ProjectBinder.newestThreadId("[]"))
        assertNull(T3ProjectBinder.newestThreadId("not json"))
    }

    @Test
    fun `thread start sends the message on stdin and returns the new id`() {
        val received = tmp.root.toPath().resolve("received.txt")
        val cli = FakeCli.create(
            tmp.root.toPath(),
            """
            printf '%s\n' "${'$'}@" > '$received'
            cat >> '$received'
            printf '{"threadId":"t-42","runId":"r-1"}'
            """.trimIndent(),
        )
        assertEquals("t-42", T3Threads.start(cli, "/work/demo", "Why?\n\nA.kt:3\n\n```kt\nval x = 1\n```"))
        assertEquals(
            listOf("thread", "start", "--json", "/work/demo", "-", "Why?", "", "A.kt:3", "", "```kt", "val x = 1", "```"),
            Files.readAllLines(received),
        )
    }

    @Test
    fun `thread send reports the exit code`() {
        val cli = FakeCli.create(tmp.root.toPath(), "echo 'ThreadNotFoundError' >&2; exit 1")
        val error = assertThrows(T3Exception::class.java) { T3Threads.send(cli, "t-1", "hi") }
        assertEquals(Reason.THREAD_FAILED, error.reason)
        assertEquals(true, error.message!!.contains("ThreadNotFoundError"))
    }
}
