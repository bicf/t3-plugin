package com.bicf.t3plugin.toolWindow

import java.net.URLDecoder

/** Routes of the T3 web UI that the plugin builds or reads. */
object T3Routes {

    // Threads started through the CLI have ids such as `mcp:1c760312-…`; RFC 3986 allows `:` in a path segment.
    private val SAFE_SEGMENT = Regex("[A-Za-z0-9_.:-]+")

    // Environment ids are UUIDs (`userdata/environment-id`). Other first segments (`draft`, `settings`, …) are app pages.
    private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    /** A thread page of the web UI: `/<environmentId>/<threadId>`. */
    data class ThreadRoute(val environmentId: String, val threadId: String) {
        val path: String get() = "/$environmentId/$threadId"
    }

    /** True when [value] can go into a URL path without escaping. `.` and `..` are never safe. */
    fun isSafeSegment(value: String): Boolean = SAFE_SEGMENT.matches(value) && value != "." && value != ".."

    /** `/<environmentId>/<threadId>`, or null when an id is not a safe segment. */
    fun threadPath(environmentId: String, threadId: String): String? =
        if (isSafeSegment(environmentId) && isSafeSegment(threadId)) "/$environmentId/$threadId" else null

    /** The thread id when [url] is `<origin>/<environmentId>/<threadId>[/…][?…][#…]`, otherwise null. */
    fun threadIdIn(url: String, origin: String, environmentId: String): String? {
        val prefix = "${origin.trimEnd('/')}/$environmentId/"
        if (!url.startsWith(prefix)) return null
        val segment = url.removePrefix(prefix).substringBefore('?').substringBefore('#').substringBefore('/')
        return segment.takeIf(::isSafeSegment)
    }

    /**
     * The thread of the page path [pathname] (`location.pathname`), or null for another page.
     * The first segment must be a UUID (the environment id) and the second, after percent-decoding, a safe segment
     * (the thread id). The T3 router writes `mcp:1c76…` as `mcp%3A1c76…`.
     */
    fun threadRoute(pathname: String): ThreadRoute? {
        val segments = pathname.substringBefore('?').substringBefore('#').trim('/').split('/')
        if (segments.size < 2) return null
        val environmentId = segments[0]
        val threadId = percentDecode(segments[1]) ?: return null
        if (!UUID.matches(environmentId) || !isSafeSegment(threadId)) return null
        return ThreadRoute(environmentId, threadId)
    }

    /** Decodes `%XX` escapes. `+` stays `+`. Returns null for an invalid escape. */
    private fun percentDecode(segment: String): String? = try {
        URLDecoder.decode(segment.replace("+", "%2B"), Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        null
    }
}
