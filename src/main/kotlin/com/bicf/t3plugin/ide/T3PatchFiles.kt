package com.bicf.t3plugin.ide

/** Picks files in a unified diff of a T3 turn. */
internal object T3PatchFiles {

    /** The path of a patch side without the `a/` or `b/` prefix of `git diff`. */
    fun clean(path: String?): String? {
        val text = path?.trim()?.takeIf { it.isNotEmpty() && it != "/dev/null" } ?: return null
        return if (text.startsWith("a/") || text.startsWith("b/")) text.substring(2) else text
    }

    /**
     * The index of the file that [hint] names in [paths], or 0. [hint] is the full relative path or the file name
     * that T3 shows. A full match wins over a match of the last segments.
     */
    fun indexOf(paths: List<String>, hint: String?): Int {
        val wanted = hint?.trim()?.trimStart('/')?.takeIf { it.isNotEmpty() } ?: return 0
        paths.indexOf(wanted).takeIf { it >= 0 }?.let { return it }
        return paths.indexOfFirst { it.endsWith("/$wanted") }.coerceAtLeast(0)
    }
}
