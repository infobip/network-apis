package com.infobip.napi.api.models.numberverify

data class ClientVerifyResultResponse(
    val result: VerifyStatus? = null,
    val token: String? = null,
    val errorDescription: String? = null,
)
