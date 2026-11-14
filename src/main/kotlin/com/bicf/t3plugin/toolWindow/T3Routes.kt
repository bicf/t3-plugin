package com.bicf.t3plugin.toolWindow

import java.net.URLDecoder

/** Routes of the T3 web UI that the plugin builds or reads. */
object T3Routes {

    // Threads started through the CLI have ids such as `mcp:1c760312-…`; RFC 3986 allows `:` in a path segment.
    private val SAFE_SEGMENT = Regex("[A-Za-z0-9_.:-]+")

    // Environment ids are UUIDs (`userdata/environment-id`). Other first segments (`draft`, `settings`, …) are app pages.
    private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    // A page path that the plugin can put into a script: segments of letters, digits and `_.:%~-`.
    private val SAFE_PATH = Regex("(/[A-Za-z0-9_.:%~-]+)+/?")

    /**
     * The page of the "T3 Code" tool window with editor tabs. T3 has no route with this path: the web UI shows only its
     * sidebar there, and each page that the user opens from the sidebar changes the path (verified with T3 0.1.9).
     */
    const val NAVIGATOR_PATH = "/ide-navigator"

    /** A thread page of the web UI: `/<environmentId>/<threadId>`. */
    data class ThreadRoute(val environmentId: String, val threadId: String) {
        val path: String get() = "/$environmentId/$threadId"
    }

    /** The kinds of pages that open in an editor tab. */
    enum class PageKind { THREAD, DRAFT, PAGE }

    /**
     * A page that opens in an editor tab. [key] identifies the tab: one tab per thread, per draft, and per app page
     * (`settings`, `pull-requests`, `usage`, …). The pages of one key share the tab, for example `/settings/general`
     * and `/settings/appearance`.
     */
    data class TabPage(val kind: PageKind, val key: String) {
        val isSettings: Boolean get() = key == "page:settings"
    }

    /**
     * The tab page of [pathname]: a thread, a draft (`/draft/<id>`) or an app page of T3 0.1.9 ([APP_PAGES]).
     * Null for any other page (`/`, [NAVIGATOR_PATH], the pairing page, a file path) and for a path with an unsafe
     * character, `.` or `..`.
     */
    fun tabPage(pathname: String): TabPage? {
        val path = pathname.substringBefore('?').substringBefore('#')
        if (!isSafePath(path)) return null
        threadRoute(path)?.let { return TabPage(PageKind.THREAD, "thread:${it.environmentId}/${it.threadId}") }
        val segments = path.trim('/').split('/')
        val first = segments[0]
        return when (first) {
            "draft" -> segments.getOrNull(1)?.takeIf(::isSafeSegment)?.let { TabPage(PageKind.DRAFT, "draft:$it") }
            in APP_PAGES -> TabPage(PageKind.PAGE, "page:$first")
            else -> null
        }
    }

    /** True when [path] starts with `/` and has only safe segments, none of them `.` or `..`. */
    fun isSafePath(path: String): Boolean =
        SAFE_PATH.matches(path) && path.split('/').none { it == "." || it == ".." }

    /** The first path segments of the app pages of T3 0.1.9 that open in a tab. */
    private val APP_PAGES = setOf("settings", "projects", "pull-requests", "usage")

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
