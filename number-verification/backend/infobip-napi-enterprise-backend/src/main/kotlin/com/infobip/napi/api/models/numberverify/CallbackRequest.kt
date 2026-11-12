package com.infobip.napi.api.models.numberverify

import com.infobip.napi.api.models.InfobipNapiError
import jakarta.validation.constraints.NotBlank

data class CallbackRequest(
    @field:NotBlank val token: String,
    val result: VerifyStatus? = null,
    val error: InfobipNapiError? = null,
)
