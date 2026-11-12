package com.infobip.napi.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class ClientVerifyResponse(
    val token: String? = null,
    val deviceRedirectUrl: String? = null,
    val errorDescription: String? = null,
)
