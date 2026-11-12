package com.bicf.t3plugin.toolWindow

import java.net.URI

/**
 * Decides where a link of the T3 web UI opens (T3LinkHandler in PLAN.md).
 *
 * Rules:
 * - Before the panel knows the server origin, every navigation stays in the panel.
 * - An http(s) URL on the server origin stays in the panel. Any other http(s) URL opens in the system browser.
 * - `about:`, `data:`, `blob:` and `javascript:` stay in the panel. `file:` is blocked.
 * - Other schemes, for example `mailto:`, go to the operating system.
 * - A popup on the server origin loads in the panel. A popup without an http(s) URL is blocked.
 */
internal object T3LinkPolicy {

    enum class Action { ALLOW, OPEN_EXTERNALLY, LOAD_IN_PLACE, BLOCK }

    private val INTERNAL_SCHEMES = setOf("about", "data", "blob", "javascript", "devtools", "chrome-devtools")

    fun forNavigation(origin: String?, url: String): Action {
        if (origin == null) return Action.ALLOW
        val uri = runCatching { URI(url) }.getOrNull() ?: return Action.BLOCK
        val scheme = uri.scheme?.lowercase() ?: return Action.BLOCK
        return when {
            scheme in INTERNAL_SCHEMES -> Action.ALLOW
            scheme == "http" || scheme == "https" -> if (sameOrigin(uri, origin)) Action.ALLOW else Action.OPEN_EXTERNALLY
            scheme == "file" -> Action.BLOCK
            else -> Action.OPEN_EXTERNALLY
        }
    }

    fun forPopup(origin: String?, url: String): Action = when (val action = forNavigation(origin, url)) {
        Action.ALLOW -> if (isHttp(url)) Action.LOAD_IN_PLACE else Action.BLOCK
        else -> action
    }

    private fun isHttp(url: String): Boolean =
        runCatching { URI(url).scheme?.lowercase() }.getOrNull().let { it == "http" || it == "https" }

    private fun sameOrigin(uri: URI, origin: String): Boolean {
        val other = runCatching { URI(origin) }.getOrNull() ?: return false
        return uri.scheme.equals(other.scheme, ignoreCase = true) &&
            uri.host.equals(other.host, ignoreCase = true) &&
            effectivePort(uri) == effectivePort(other)
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port > 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }
}
