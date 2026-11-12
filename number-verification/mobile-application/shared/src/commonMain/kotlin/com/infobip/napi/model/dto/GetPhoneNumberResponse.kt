package com.infobip.napi.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class GetPhoneNumberResponse(
    val phoneNumber: String? = null,
)
