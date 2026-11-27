package com.bicf.t3plugin.server

import com.intellij.util.EnvironmentUtil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * The T3 Code data directory.
 * `T3CODE_HOME` overrides the default `~/.t3`, as in the `t3` wrapper script of the desktop app.
 */
data class T3Home(val dir: Path) {

    /** The file that the running server writes. It contains pid, port and origin. */
    val runtimeFile: Path
        get() = dir.resolve("userdata").resolve("server-runtime.json")

    /** The file with the id of this T3 environment. The web UI uses the id in thread routes. */
    val environmentIdFile: Path
        get() = dir.resolve("userdata").resolve("environment-id")

    /** The settings of the T3 server ("Settings" in the T3 web UI). */
    val settingsFile: Path
        get() = dir.resolve("userdata").resolve("settings.json")

    /**
     * The directory of the Git worktrees that T3 creates for its threads: the T3 setting `worktreesDirectory`
     * ("Worktree location"), or `<home>/worktrees` when the setting is empty, missing or not valid. Reads the settings.
     */
    val worktreesDir: Path
        get() {
            val text = try {
                Files.readString(settingsFile)
            } catch (e: IOException) {
                null
            }
            return worktreesDirectory(text, userHome()) ?: dir.resolve("worktrees")
        }

    companion object {
        const val HOME_ENV = "T3CODE_HOME"

        /** Resolves the home from the shell environment of the IDE. */
        fun detect(): T3Home = resolve(EnvironmentUtil.getEnvironmentMap(), userHome())

        fun resolve(env: Map<String, String>, userHome: Path): T3Home {
            val override = env[HOME_ENV]?.takeIf { it.isNotBlank() }
            return T3Home(override?.let { Path.of(it) } ?: userHome.resolve(".t3"))
        }

        internal fun userHome(): Path = Path.of(System.getProperty("user.home"))

        /**
         * The value of `worktreesDirectory` in the T3 settings [json], as T3 0.1.9 reads it: `~` and `~/` start in
         * [userHome]; the path must be absolute and must not be a filesystem root. Null for an empty or invalid value.
         */
        internal fun worktreesDirectory(json: String?, userHome: Path): Path? {
            val settings = json?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return null
            val value = (settings["worktreesDirectory"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()
            if (value.isNullOrEmpty()) return null
            val path = try {
                when {
                    value == "~" -> userHome
                    value.startsWith("~/") -> userHome.resolve(value.substring(2))
                    else -> Path.of(value)
                }.normalize()
            } catch (e: InvalidPathException) {
                return null
            }
            return path.takeIf { it.isAbsolute && it.parent != null }
        }
    }
}
