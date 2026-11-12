package com.bicf.t3plugin.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** The fields of `userdata/server-runtime.json` that the plugin uses. */
data class T3Runtime(val pid: Long, val port: Int, val origin: String) {

    /**
     * True when the server process is alive and the origin accepts a TCP connection.
     * `/readyz` is not a health check: it returns the HTML of the web UI.
     */
    fun isReachable(connectTimeout: Duration = Duration.ofMillis(500)): Boolean {
        val alive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)
        return alive && acceptsConnections(origin, connectTimeout)
    }

    companion object {
        /** Returns null when [json] has no valid pid, no valid port, or no http(s) origin. */
        fun parse(json: String): T3Runtime? = runCatching {
            val fields = Json.parseToJsonElement(json).jsonObject
            val pid = fields["pid"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: return null
            val port = fields["port"]?.jsonPrimitive?.intOrNull?.takeIf { it in 1..65535 } ?: return null
            val origin = fields["origin"]?.jsonPrimitive?.contentOrNull?.trimEnd('/') ?: return null
            val uri = URI(origin)
            if (uri.scheme !in setOf("http", "https") || uri.host == null) return null
            T3Runtime(pid, port, origin)
        }.getOrNull()

        /** Returns null when [file] is missing, unreadable or not valid. */
        fun read(file: Path): T3Runtime? = try {
            if (Files.isRegularFile(file)) parse(Files.readString(file)) else null
        } catch (e: IOException) {
            null
        }
    }
}

/** True when the host and port of [origin] accept a TCP connection. The default port is 80 for http and 443 for https. */
fun acceptsConnections(origin: String, connectTimeout: Duration = Duration.ofMillis(500)): Boolean {
    val uri = runCatching { URI(origin) }.getOrNull() ?: return false
    val host = uri.host ?: return false
    val port = when {
        uri.port > 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }
    return try {
        Socket().use { it.connect(InetSocketAddress(host, port), connectTimeout.toMillis().toInt()) }
        true
    } catch (e: IOException) {
        false
    }
}
