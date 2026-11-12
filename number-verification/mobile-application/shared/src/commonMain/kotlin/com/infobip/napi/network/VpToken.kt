package com.infobip.napi.network

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Takes the `vp_token` out of the credential JSON that CredentialManager returns. The token itself is not read. */
object VpToken {

    /** Returns `data.vp_token`'s first entry, index 0, or null when the credential JSON has no token. */
    fun extract(credentialJson: String): String? = runCatching {
        val vpToken = networkJson.parseToJsonElement(credentialJson)
            .jsonObject["data"]
            ?.jsonObject?.get("vp_token")
            ?.jsonObject ?: return null
        vpToken.values.firstOrNull()?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
    }.getOrNull()

    /**
     * Whether the credential JSON is an OpenID4VP error the OS returned instead of a token, such as
     * `{"error":"server_error","error_description":"FAILED_TO_GET_TEMP_TOKEN"}`: a non-blank `error` or
     * `error_description`, at the top level or under `data`.
     */
    fun hasError(credentialJson: String): Boolean = runCatching {
        val root = networkJson.parseToJsonElement(credentialJson).jsonObject
        listOfNotNull(root, root["data"] as? JsonObject).any(::hasErrorField)
    }.getOrDefault(false)

    private fun hasErrorField(json: JsonObject): Boolean =
        listOf("error", "error_description").any { key ->
            (json[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.isNotBlank() == true
        }
}
