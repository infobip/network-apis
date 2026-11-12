package com.infobip.napi.api.models.auth

import com.fasterxml.jackson.annotation.JsonProperty

data class OAuthErrorResponse(
    val error: String,
    @get:JsonProperty("error_description") val errorDescription: String,
)
