package com.bicf.t3plugin.server

import com.intellij.openapi.util.SystemInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class T3ServeProcessTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `stop ends the wrapper script and its child process`() {
        assumeFalse("The fake CLI is a POSIX shell script.", SystemInfo.isWindows)
        val home = T3Home(tmp.newFolder("home").toPath())
        val childPidFile = tmp.root.toPath().resolve("child.pid")
        val script = home.dir.resolve("bin/t3")
        Files.createDirectories(script.parent)
        // Like the real `t3` wrapper: the long-running process is a child, not an exec.
        Files.writeString(script, "#!/bin/sh\nsleep 60 &\necho \$! > '$childPidFile'\nwait\n")
        script.toFile().setExecutable(true)

        val server = T3ServeProcess.start(T3Cli(script, home, workingDirectory = tmp.root.toPath()))
        waitUntil { Files.isRegularFile(childPidFile) && Files.readString(childPidFile).isNotBlank() }
        val child = ProcessHandle.of(Files.readString(childPidFile).trim().toLong()).orElseThrow()
        assertTrue(child.isAlive)
        assertNull(server.exitCodeOrNull())

        server.stop()

        waitUntil { server.exitCodeOrNull() != null && !child.isAlive }
        assertNotNull(server.exitCodeOrNull())
        assertFalse(child.isAlive)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(50)
    }
}
