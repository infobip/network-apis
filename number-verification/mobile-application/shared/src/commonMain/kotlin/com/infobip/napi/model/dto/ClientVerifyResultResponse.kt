package com.infobip.napi.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class ClientVerifyResultResponse(
    val errorDescription: String? = null,
    val token: String? = null,
    val result: String? = null,
)
