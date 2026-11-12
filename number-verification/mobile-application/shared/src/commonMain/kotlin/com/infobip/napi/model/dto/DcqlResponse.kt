package com.infobip.napi.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class DcqlResponse(
    val nonce: String? = null,
    val carrierId: Int? = null,
    val credentialRequest: String? = null,
)
