package com.bicf.t3plugin.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import java.net.URI
import java.nio.file.Path

/** Settings of the T3 plugin, in Settings | Tools | T3 Code. */
@Service(Service.Level.APP)
@State(name = "T3CodeSettings", storages = [Storage("t3-code.xml")])
class T3Settings : SimplePersistentStateComponent<T3Settings.State>(State()) {

    class State : BaseState() {
        /** Path of the `t3` command. Empty: the plugin searches `<home>/bin`, `~/.t3/bin` and `PATH`. */
        var cliPath by string()

        /** URL of a remote T3 server. Empty: the plugin uses the server on this machine. */
        var remoteUrl by string()

        /** Start `t3 serve --no-browser` when no local server answers. */
        var autoStartServer by property(true)

        /** Open each conversation in an editor tab. Off: the tool window shows the conversations. */
        var openThreadsInEditor by property(true)

        /** Use the IDE editor, diff viewer, terminal and Git instead of the T3 panels, when the IDE has them. */
        var useIdeTools by property(true)
    }

    /** The configured `t3` command, or null when the plugin searches for it. */
    val cliPath: Path?
        get() = state.cliPath?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { Path.of(it) }.getOrNull() }

    /** The origin of the remote server, or null for the local server. */
    val remoteOrigin: String?
        get() = normalizeOrigin(state.remoteUrl)

    val autoStartServer: Boolean
        get() = state.autoStartServer

    val openThreadsInEditor: Boolean
        get() = state.openThreadsInEditor

    val useIdeTools: Boolean
        get() = state.useIdeTools

    companion object {
        fun getInstance(): T3Settings = service()

        /** Returns `scheme://host[:port]` of an http(s) URL. Returns null for an empty or invalid value. */
        fun normalizeOrigin(value: String?): String? {
            val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val uri = runCatching { URI(text) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
            val host = uri.host?.lowercase() ?: return null
            val port = if (uri.port > 0) ":${uri.port}" else ""
            return "$scheme://$host$port"
        }
    }
}
