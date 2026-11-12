package com.infobip.napi.api.models.auth

import com.fasterxml.jackson.annotation.JsonProperty

data class AccessTokenResponse(
    @get:JsonProperty("access_token") val accessToken: String,
    @get:JsonProperty("token_type") val tokenType: String,
    @get:JsonProperty("expires_in") val expiresIn: Long,
) {
    override fun toString() = "AccessTokenResponse(tokenType=$tokenType, expiresIn=$expiresIn)"
}
