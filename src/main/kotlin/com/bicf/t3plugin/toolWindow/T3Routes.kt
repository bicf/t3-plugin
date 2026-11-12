package com.bicf.t3plugin.toolWindow

/** Routes of the T3 web UI that the plugin builds or reads. */
object T3Routes {

    private val SAFE_SEGMENT = Regex("[A-Za-z0-9_-]+")

    /** True when [value] can go into a URL path without escaping. */
    fun isSafeSegment(value: String): Boolean = SAFE_SEGMENT.matches(value)

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
}
