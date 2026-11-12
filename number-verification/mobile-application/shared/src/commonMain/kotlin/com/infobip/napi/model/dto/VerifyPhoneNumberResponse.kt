package com.infobip.napi.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class VerifyPhoneNumberResponse(
    val verified: Boolean? = null,
)
