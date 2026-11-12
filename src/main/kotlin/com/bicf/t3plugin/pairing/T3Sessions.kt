package com.bicf.t3plugin.pairing

import com.bicf.t3plugin.server.T3Cli
import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration

/** Sessions on the T3 server, from `t3 auth session list --json`. The list never contains bearer tokens. */
internal object T3Sessions {

    private val LOG = logger<T3Sessions>()

    /** Ids of the sessions whose `client.label` is exactly [label]. Returns an empty list for content that is not valid. */
    fun idsWithLabel(json: String, label: String): List<String> = runCatching {
        Json.parseToJsonElement(json).jsonArray.mapNotNull { element ->
            val session = element.jsonObject
            val clientLabel = session["client"]?.jsonObject?.get("label")?.jsonPrimitive?.contentOrNull
            session["sessionId"]?.jsonPrimitive?.contentOrNull?.takeIf { clientLabel == label }
        }
    }.getOrDefault(emptyList())

    /**
     * Revokes every session with [label] and returns how many it revoked.
     * A failure only writes a warning: an old session must never block a new pairing.
     */
    fun revokeWithLabel(cli: T3Cli, label: String, timeout: Duration = Duration.ofSeconds(60)): Int {
        val list = cli.run(timeout, "auth", "session", "list", "--json")
        if (list.exitCode != 0) {
            LOG.warn("`t3 auth session list` stopped with exit code ${list.exitCode}. ${list.lastErrorLine}")
            return 0
        }
        return idsWithLabel(list.stdout, label).count { id ->
            val revoke = cli.run(timeout, "auth", "session", "revoke", id)
            if (revoke.exitCode != 0) LOG.warn("Could not revoke T3 session $id: exit code ${revoke.exitCode}. ${revoke.lastErrorLine}")
            revoke.exitCode == 0
        }
    }
}
