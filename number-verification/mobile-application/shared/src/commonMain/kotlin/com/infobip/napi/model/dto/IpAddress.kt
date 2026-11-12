package com.infobip.napi.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class IpAddress(
    val address: String,
    val port: Int,
)
