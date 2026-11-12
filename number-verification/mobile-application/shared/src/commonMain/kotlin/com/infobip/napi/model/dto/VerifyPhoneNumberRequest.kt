package com.infobip.napi.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class VerifyPhoneNumberRequest(
    val vpToken: String,
    val phoneNumber: String,
)
