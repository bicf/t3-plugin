package com.bicf.t3plugin.server

import com.intellij.openapi.util.SystemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class T3CliTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val root: Path get() = tmp.root.toPath()
    private val home: T3Home get() = T3Home(root.resolve("custom-home"))
    private val userHome: Path get() = root.resolve("user")

    private fun script(path: Path, executable: Boolean = true): Path {
        Files.createDirectories(path.parent)
        Files.writeString(path, "#!/bin/sh\n")
        path.toFile().setExecutable(executable)
        return path
    }

    @Test
    fun `prefers bin in the T3 home`() {
        val inHome = script(home.dir.resolve("bin/t3"))
        script(userHome.resolve(".t3/bin/t3"))
        assertEquals(inHome, T3Cli.locate(home, userHome, isWindows = false) { null })
    }

    @Test
    fun `falls back to the desktop app wrapper in the user home`() {
        val wrapper = script(userHome.resolve(".t3/bin/t3"))
        assertEquals(wrapper, T3Cli.locate(home, userHome, isWindows = false) { null })
    }

    @Test
    fun `falls back to PATH`() {
        val fromPath = Path.of("/usr/local/bin/t3")
        assertEquals(fromPath, T3Cli.locate(home, userHome, isWindows = false) { if (it == "t3") fromPath else null })
    }

    @Test
    fun `skips a file that is not executable`() {
        assumeFalse("Windows ignores the executable flag.", SystemInfo.isWindows)
        script(home.dir.resolve("bin/t3"), executable = false)
        assertNull(T3Cli.locate(home, userHome, isWindows = false) { null })
    }

    @Test
    fun `every command gets the home in T3CODE_HOME and no extra argument`() {
        val cli = T3Cli(Path.of("/opt/t3"), T3Home(Path.of("/data/t3")), workingDirectory = root)
        val command = cli.command("project", "add", "/work/demo")
        assertEquals(listOf("/opt/t3", "project", "add", "/work/demo"), command.getCommandLineList(null))
        assertEquals("/data/t3", command.environment["T3CODE_HOME"])
    }

    @Test
    fun `a configured path wins over the search`() {
        val configured = script(root.resolve("custom/t3"))
        script(home.dir.resolve("bin/t3"))
        assertEquals(configured, T3Cli.locate(home, userHome, isWindows = false, configured = configured) { null })
    }

    @Test
    fun `a configured path that does not exist gives no command and no fallback`() {
        script(home.dir.resolve("bin/t3"))
        assertNull(T3Cli.locate(home, userHome, isWindows = false, configured = root.resolve("missing/t3")) { null })
    }

    @Test
    fun `run writes stdin and reads stdout`() {
        assumeFalse("The fake CLI is a POSIX shell script.", SystemInfo.isWindows)
        val cli = FakeCli.create(root, "cat")
        val result = cli.run(java.time.Duration.ofSeconds(10), listOf("thread", "send", "id", "-"), stdin = "line 1\nline 2")
        assertEquals(0, result.exitCode)
        assertEquals("line 1\nline 2", result.stdout)
    }

    @Test
    fun `finds the first executable in PATH`() {
        assumeFalse("Windows ignores the executable flag.", SystemInfo.isWindows)
        script(root.resolve("a/t3"), executable = false)
        val second = script(root.resolve("b/t3"))
        val path = listOf(root.resolve("missing"), root.resolve("a"), root.resolve("b")).joinToString(java.io.File.pathSeparator)
        assertEquals(second, T3Cli.findInPath(path, "t3"))
        assertNull(T3Cli.findInPath(null, "t3"))
    }
}
