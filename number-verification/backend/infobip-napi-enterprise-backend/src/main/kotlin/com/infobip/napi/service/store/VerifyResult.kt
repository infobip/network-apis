package com.infobip.napi.service.store

import com.infobip.napi.api.models.numberverify.VerifyStatus

data class VerifyResult(
    val token: String,
    val status: VerifyStatus,
    val errorDescription: String? = null,
) {
    companion object {
        fun error(token: String, errorDescription: String?) = VerifyResult(token, VerifyStatus.ERROR, errorDescription)
    }
}
