package com.bicf.t3plugin.toolWindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class T3RoutesAndThemeTest {

    private val origin = "http://127.0.0.1:3773"
    private val env = "3d55f139-d277-48d0-8c5c-2546aeb0874a"

    @Test
    fun `builds a thread path only from safe ids`() {
        assertEquals("/$env/thread-1", T3Routes.threadPath(env, "thread-1"))
        assertNull(T3Routes.threadPath(env, "../admin"))
        assertNull(T3Routes.threadPath("a/b", "thread-1"))
    }

    @Test
    fun `reads the thread id of a thread URL`() {
        assertEquals("thread-1", T3Routes.threadIdIn("$origin/$env/thread-1", origin, env))
        assertEquals("thread-1", T3Routes.threadIdIn("$origin/$env/thread-1/files?x=1#y", origin, env))
        assertNull(T3Routes.threadIdIn("$origin/", origin, env))
        assertNull(T3Routes.threadIdIn("$origin/other-env/thread-1", origin, env))
        assertNull(T3Routes.threadIdIn("http://127.0.0.1:3899/$env/thread-1", origin, env))
    }

    @Test
    fun `theme script writes the storage key and sends a storage event`() {
        val script = T3ThemeSync.script("dark")
        assertTrue(script.contains("'t3code:theme-appearance-mode'"))
        assertTrue(script.contains("v='dark'"))
        assertTrue(script.contains("new StorageEvent('storage'"))
    }

    @Test
    fun `theme script accepts only light and dark`() {
        assertThrows(IllegalArgumentException::class.java) { T3ThemeSync.script("system") }
        assertThrows(IllegalArgumentException::class.java) { T3ThemeSync.script("dark';alert(1);'") }
    }
}
