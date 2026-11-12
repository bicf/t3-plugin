package com.bicf.t3plugin.server

import com.intellij.util.EnvironmentUtil
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

    companion object {
        const val HOME_ENV = "T3CODE_HOME"

        /** Resolves the home from the shell environment of the IDE. */
        fun detect(): T3Home = resolve(EnvironmentUtil.getEnvironmentMap(), userHome())

        fun resolve(env: Map<String, String>, userHome: Path): T3Home {
            val override = env[HOME_ENV]?.takeIf { it.isNotBlank() }
            return T3Home(override?.let { Path.of(it) } ?: userHome.resolve(".t3"))
        }

        internal fun userHome(): Path = Path.of(System.getProperty("user.home"))
    }
}
