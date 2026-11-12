package com.infobip.napi.api.models.numberverify

data class ClientVerifyResponse(
    val token: String,
    val deviceRedirectUrl: String?
)
