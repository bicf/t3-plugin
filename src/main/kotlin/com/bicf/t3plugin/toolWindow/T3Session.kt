package com.bicf.t3plugin.toolWindow

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Reads the answer of `GET /api/auth/session`, for example `{"authenticated":false,"auth":{…}}`. */
internal object T3Session {

    const val PATH = "/api/auth/session"

    /** True only when the body is a JSON object with `"authenticated": true`. */
    fun isAuthenticated(body: String): Boolean = runCatching {
        Json.parseToJsonElement(body).jsonObject["authenticated"]?.jsonPrimitive?.booleanOrNull == true
    }.getOrDefault(false)
}
