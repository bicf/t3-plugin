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
    fun `every command gets the base dir of the home`() {
        val cli = T3Cli(Path.of("/opt/t3"), T3Home(Path.of("/data/t3")), workingDirectory = root)
        assertEquals(
            listOf("/opt/t3", "auth", "pairing", "create", "--base-dir", "/data/t3"),
            cli.command("auth", "pairing", "create").getCommandLineList(null),
        )
    }
}
