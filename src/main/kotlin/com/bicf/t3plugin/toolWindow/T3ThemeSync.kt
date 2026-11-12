package com.bicf.t3plugin.toolWindow

import com.intellij.ui.JBColor

/**
 * Passes the light or dark theme of the IDE to the T3 web UI (T3ThemeSync in PLAN.md).
 *
 * The web UI stores its mode in localStorage under [STORAGE_KEY], with the values `light`, `dark` or `system`,
 * and listens to `storage` events. The script writes the key and sends a `storage` event, so the page changes theme
 * without a reload. JCEF keeps localStorage in the IDE profile: the T3 desktop app does not change.
 */
internal object T3ThemeSync {

    const val STORAGE_KEY = "t3code:theme-appearance-mode"

    /** `light` or `dark`, from the current Look and Feel of the IDE. */
    fun currentMode(): String = if (JBColor.isBright()) "light" else "dark"

    /** A script that sets [mode] and does nothing when the page has it already. */
    fun script(mode: String): String {
        require(mode == "light" || mode == "dark") { "Unsupported theme mode: $mode" }
        return "(function(){try{var k='$STORAGE_KEY',v='$mode';" +
            "if(window.localStorage.getItem(k)===v)return;" +
            "window.localStorage.setItem(k,v);" +
            "window.dispatchEvent(new StorageEvent('storage',{key:k,newValue:v,storageArea:window.localStorage}));" +
            "}catch(e){}})();"
    }
}
