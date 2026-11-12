package com.bicf.t3plugin.pairing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A one-time `/pair#token=...` link from `t3 auth pairing create`.
 * The link is a secret: [toString] hides the token, and the plugin never logs [url].
 */
class PairingLink(val url: String, val expiresAt: String?) {

    override fun toString(): String = "PairingLink(${url.substringBefore('#')}#<redacted>, expiresAt=$expiresAt)"

    companion object {
        /**
         * Parses the `--json` output of `t3 auth pairing create`.
         * Returns null when `pairUrl` is missing, or does not start with `<origin>/pair#token=` followed by a token.
         */
        fun parse(json: String, origin: String): PairingLink? = runCatching {
            val fields = Json.parseToJsonElement(json).jsonObject
            val url = fields["pairUrl"]?.jsonPrimitive?.contentOrNull ?: return null
            val prefix = "${origin.trimEnd('/')}/pair#token="
            if (!url.startsWith(prefix) || url.length == prefix.length) return null
            PairingLink(url, fields["expiresAt"]?.jsonPrimitive?.contentOrNull)
        }.getOrNull()

        /**
         * Reads a link that the user pasted, for example from `t3 pair` on a remote host.
         * Returns null when the text is not `<origin>/pair#token=<token>`, or when the token contains white space.
         */
        fun fromPasted(text: String?, origin: String): PairingLink? {
            val url = text?.trim() ?: return null
            val prefix = "${origin.trimEnd('/')}/pair#token="
            val token = url.removePrefix(prefix)
            if (!url.startsWith(prefix) || token.isEmpty() || token.any { it.isWhitespace() }) return null
            return PairingLink(url, null)
        }
    }
}
