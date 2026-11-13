package com.bicf.t3plugin.toolWindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color

class T3RoutesAndThemeTest {

    private val origin = "http://127.0.0.1:3773"
    private val env = "3d55f139-d277-48d0-8c5c-2546aeb0874a"

    @Test
    fun `builds a thread path only from safe ids`() {
        assertEquals("/$env/thread-1", T3Routes.threadPath(env, "thread-1"))
        // Id format of a thread started with `t3 thread start` (T3 Code 0.1.9).
        assertEquals("/$env/mcp:1c760312-d32b-4eb2-b15e-1ccbcc0f578b", T3Routes.threadPath(env, "mcp:1c760312-d32b-4eb2-b15e-1ccbcc0f578b"))
        assertNull(T3Routes.threadPath(env, "../admin"))
        assertNull(T3Routes.threadPath(env, ".."))
        assertNull(T3Routes.threadPath("a/b", "thread-1"))
        assertNull(T3Routes.threadPath(env, "a?b"))
    }

    @Test
    fun `reads the thread id of a thread URL`() {
        assertEquals("thread-1", T3Routes.threadIdIn("$origin/$env/thread-1", origin, env))
        assertEquals("thread-1", T3Routes.threadIdIn("$origin/$env/thread-1/files?x=1#y", origin, env))
        assertEquals("mcp:1c760312", T3Routes.threadIdIn("$origin/$env/mcp:1c760312", origin, env))
        assertNull(T3Routes.threadIdIn("$origin/", origin, env))
        assertNull(T3Routes.threadIdIn("$origin/other-env/thread-1", origin, env))
        assertNull(T3Routes.threadIdIn("http://127.0.0.1:3899/$env/thread-1", origin, env))
    }

    @Test
    fun `theme script stores and selects the IDE theme, then sends storage events`() {
        val script = T3ThemeSync.script(T3ThemeSync.Palette("dark", mapOf("canvas" to "#2b2d30", "text" to "#dfe1e5")))
        assertTrue(script.contains("\"id\":\"jetbrains-ide\""))
        assertTrue(script.contains("\"appearance\":\"dark\""))
        assertTrue(script.contains("\"colors\":{\"canvas\":\"#2b2d30\",\"text\":\"#dfe1e5\"}"))
        assertTrue(script.contains("'t3code:themes:v1'"))
        assertTrue(script.contains("set('t3code:theme',t.id)"))
        assertTrue(script.contains("set('t3code:theme-appearance-mode',t.appearance)"))
        assertTrue(script.contains("removeItem('t3code:theme-halves:v1')"))
        assertTrue(script.contains("new StorageEvent('storage'"))
    }

    @Test
    fun `palette accepts only light or dark, known keys and hex colors`() {
        assertThrows(IllegalArgumentException::class.java) { T3ThemeSync.Palette("system", emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) { T3ThemeSync.Palette("dark", mapOf("canvas" to "red")) }
        assertThrows(IllegalArgumentException::class.java) { T3ThemeSync.Palette("dark", mapOf("canvas" to "#000000\"};alert(1)//")) }
        assertThrows(IllegalArgumentException::class.java) { T3ThemeSync.Palette("dark", mapOf("x\":1" to "#000000")) }
    }

    @Test
    fun `hex keeps alpha only when the color is not opaque`() {
        assertEquals("#2b2d30", T3IdePalette.hex(Color(0x2b, 0x2d, 0x30)))
        assertEquals("#ffffff80", T3IdePalette.hex(Color(255, 255, 255, 128)))
    }
}
