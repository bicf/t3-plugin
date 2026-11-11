package com.bicf.t3plugin.server

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path

class T3HomeTest {

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
}
