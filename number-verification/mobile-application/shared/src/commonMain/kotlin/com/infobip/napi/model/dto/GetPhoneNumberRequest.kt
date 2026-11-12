package com.infobip.napi.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class GetPhoneNumberRequest(
    val vpToken: String,
)
