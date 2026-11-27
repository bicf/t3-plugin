package com.bicf.t3plugin.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class T3HomeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val userHome = Path.of("/home/user")

    @Test
    fun `uses T3CODE_HOME when set`() {
        assertEquals(Path.of("/data/t3"), T3Home.resolve(mapOf("T3CODE_HOME" to "/data/t3"), userHome).dir)
    }

    @Test
    fun `uses dot t3 in the user home when T3CODE_HOME is missing or blank`() {
        assertEquals(userHome.resolve(".t3"), T3Home.resolve(emptyMap(), userHome).dir)
        assertEquals(userHome.resolve(".t3"), T3Home.resolve(mapOf("T3CODE_HOME" to " "), userHome).dir)
    }

    @Test
    fun `runtime file is in userdata`() {
        assertEquals(
            Path.of("/data/t3/userdata/server-runtime.json"),
            T3Home(Path.of("/data/t3")).runtimeFile,
        )
    }

    @Test
    fun `reads the worktrees directory of the T3 settings`() {
        fun read(value: String) = T3Home.worktreesDirectory("""{"worktreesDirectory":$value}""", userHome)
        assertEquals(Path.of("/data/worktrees"), read("\"/data/worktrees\""))
        assertEquals(userHome.resolve("wt"), read("\"~/wt\""))
        assertEquals(userHome, read("\"~\""))
        assertNull(read("\"\""))
        assertNull(read("\"relative/wt\""))
        assertNull(read("\"/\""))
        assertNull(read("42"))
        assertNull(T3Home.worktreesDirectory("{}", userHome))
        assertNull(T3Home.worktreesDirectory("not json", userHome))
        assertNull(T3Home.worktreesDirectory(null, userHome))
    }

    @Test
    fun `the worktrees folder is the T3 setting, or worktrees in the home`() {
        val home = T3Home(tmp.root.toPath().resolve("t3"))
        assertEquals(home.dir.resolve("worktrees"), home.worktreesDir)
        Files.createDirectories(home.settingsFile.parent)
        Files.writeString(home.settingsFile, """{"worktreesDirectory":""}""")
        assertEquals(home.dir.resolve("worktrees"), home.worktreesDir)
        val custom = tmp.root.toPath().resolve("custom")
        Files.writeString(home.settingsFile, """{"favorites":[],"worktreesDirectory":"$custom"}""")
        assertEquals(custom, home.worktreesDir)
    }
}
