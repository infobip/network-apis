package com.infobip.napi.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val errorDescription: String? = null,
)
