package com.infobip.napi.client.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.infobip.napi.api.models.InfobipNapiError

data class InfobipSmvVerifyRequest(
    val consentGranted: Boolean,
    val phoneNumber: String,
    val callbackUrl: String,
    val returnUrl: String?,
    val deviceIp: String?,
    val devicePort: Int?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfobipSmvVerifyResponse(
    val status: InfobipSmvStatus? = null,
    val token: String? = null,
    val deviceRedirectUrl: String? = null,
    val error: InfobipNapiError? = null,
)

enum class InfobipSmvStatus {
    OK,
    REDIRECT,
    ERROR,
}
