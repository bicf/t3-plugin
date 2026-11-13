package com.bicf.t3plugin.ide

import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * A file location from the T3 web UI: an absolute path, with an optional line and column (1-based).
 *
 * T3 writes a location in 2 ways, and the parser accepts both (same rules as `fileLinks` in the web UI):
 * - a suffix of the path: `file.kt:12`, `file.kt:12:3`, `file.kt:12-20`;
 * - a link fragment: `#L12`, `#L12C3`, `#L12-L20`.
 * A suffix wins over a fragment. Line or column 0 means "no line" or "no column".
 */
data class T3FileTarget(val path: String, val line: Int? = null, val column: Int? = null) {

    /** The path as a local file path, or null when it is not absolute. Accepts `/C:/x` (a `file:` URL path) on Windows. */
    fun toLocalPath(): Path? {
        val text = if (WINDOWS_URL_PATH.matches(path)) path.substring(1) else path
        val local = try {
            Path.of(text)
        } catch (e: InvalidPathException) {
            return null
        }
        return local.takeIf { it.isAbsolute }?.normalize()
    }

    companion object {
        private val SUFFIX = Regex(":(\\d+)(?::(\\d+)|-(\\d+))?$")
        private val FRAGMENT = Regex("^#L(\\d+)(?:C(\\d+)|-L?(\\d+))?$", RegexOption.IGNORE_CASE)
        private val WINDOWS_URL_PATH = Regex("^/[A-Za-z]:[/\\\\].*")

        fun parse(path: String, hash: String? = null): T3FileTarget {
            val suffix = SUFFIX.find(path)
            val match = suffix ?: hash?.let { FRAGMENT.find(it) } ?: return T3FileTarget(path)
            val line = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
            val column = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 }
            val bare = if (suffix != null) path.substring(0, suffix.range.first) else path
            return T3FileTarget(bare, line, if (line != null) column else null)
        }
    }
}
