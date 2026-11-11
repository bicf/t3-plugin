package com.bicf.t3plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class T3BundleTest : BasePlatformTestCase() {

    fun testToolWindowMessagesResolve() {
        assertEquals("T3 Code will appear here.", T3Bundle["toolWindow.placeholder"])
        assertEquals(
            "This IDE runtime does not support the embedded browser (JCEF).",
            T3Bundle["toolWindow.jcefUnsupported"],
        )
    }
}
