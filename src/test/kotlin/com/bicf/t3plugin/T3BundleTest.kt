package com.bicf.t3plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class T3BundleTest : BasePlatformTestCase() {

    fun testToolWindowMessagesResolve() {
        assertEquals("Connecting to the T3 server…", T3Bundle["toolWindow.connecting"])
        assertEquals(
            "This IDE runtime does not support the embedded browser (JCEF).",
            T3Bundle["toolWindow.jcefUnsupported"],
        )
        assertEquals(
            "The T3 server at http://127.0.0.1:3773 does not answer: refused",
            T3Bundle.message("toolWindow.serverNotAnswering", "http://127.0.0.1:3773", "refused"),
        )
        assertEquals("Reconnect to T3", T3Bundle.messagePointer("action.reconnect.text").get())
    }
}
