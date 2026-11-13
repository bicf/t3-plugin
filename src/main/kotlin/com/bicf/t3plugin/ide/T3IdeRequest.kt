package com.bicf.t3plugin.ide

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * A request of the IDE bridge script in the T3 page (see [T3IdeBridge]): the page asks the IDE to do the job
 * of a T3 tool. The plugin answers each request with "handled" or "not handled"; on "not handled" the page
 * lets T3 do the job.
 */
sealed interface T3IdeRequest {
    /** Request number of the page, from 1 to [MAX_ID]. The answer script uses it. */
    val id: Long

    /** A click on a file link in the conversation. */
    data class OpenFile(override val id: Long, val target: T3FileTarget) : T3IdeRequest

    /** `shell.openInEditor` from T3 ("Open in …" and the other editor buttons): a file or a directory. */
    data class OpenInEditor(override val id: Long, val target: T3FileTarget) : T3IdeRequest

    /** The diff of a turn, from the answer of `orchestration.getTurnDiff`. [fileHint] is the file that the user clicked. */
    data class TurnDiff(
        override val id: Long,
        val patch: String,
        val fromTurn: Int?,
        val toTurn: Int?,
        val fileHint: String?,
    ) : T3IdeRequest

    /** "Toggle terminal drawer", or the "Terminal" panel surface. [cwd] is the workspace of the thread. */
    data class Terminal(override val id: Long, val cwd: String) : T3IdeRequest

    /** The "Files" panel surface. */
    data class Files(override val id: Long, val cwd: String) : T3IdeRequest

    /** "Commit, push & PR" (only when the label starts with "Commit"). */
    data class Commit(override val id: Long, val cwd: String) : T3IdeRequest

    /** The branch selector of the thread. */
    data class Branches(override val id: Long, val cwd: String) : T3IdeRequest

    /** "Changes vs <ref>": the working tree of [cwd] against [ref]. */
    data class BranchDiff(override val id: Long, val cwd: String, val ref: String) : T3IdeRequest

    companion object {
        const val PREFIX = "ide:"
        const val MAX_ID = 999_999_999L

        private val REF = Regex("[A-Za-z0-9._/@{}^~-]{1,200}")

        /**
         * Reads the JSON object after [PREFIX]. Returns null for an unknown kind, a missing field or an invalid id.
         * The page is the T3 web UI, but the conversation shows text from an agent: each field is checked.
         */
        fun parse(json: String): T3IdeRequest? {
            val obj = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
            val id = obj.long("id")?.takeIf { it in 1..MAX_ID } ?: return null
            return when (obj.string("kind")) {
                "file" -> obj.string("path")?.let { OpenFile(id, T3FileTarget.parse(it, obj.string("hash"))) }
                "open" -> obj.string("path")?.let { OpenInEditor(id, T3FileTarget.parse(it)) }
                "diff" -> obj.string("patch")?.let {
                    TurnDiff(id, it, obj.int("fromTurn"), obj.int("toTurn"), obj.string("file")?.takeIf(String::isNotBlank))
                }
                "terminal" -> obj.cwd()?.let { Terminal(id, it) }
                "files" -> obj.cwd()?.let { Files(id, it) }
                "commit" -> obj.cwd()?.let { Commit(id, it) }
                "branches" -> obj.cwd()?.let { Branches(id, it) }
                "branchDiff" -> {
                    val cwd = obj.cwd() ?: return null
                    val ref = obj.string("ref")?.takeIf { REF.matches(it) && !it.startsWith("-") } ?: return null
                    BranchDiff(id, cwd, ref)
                }
                else -> null
            }
        }

        private fun JsonObject.string(key: String): String? = runCatching { get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()
        private fun JsonObject.long(key: String): Long? = runCatching { get(key)?.jsonPrimitive?.longOrNull }.getOrNull()
        private fun JsonObject.int(key: String): Int? = runCatching { get(key)?.jsonPrimitive?.intOrNull }.getOrNull()
        private fun JsonObject.cwd(): String? = string("cwd")?.takeIf { it.isNotBlank() }
    }
}
