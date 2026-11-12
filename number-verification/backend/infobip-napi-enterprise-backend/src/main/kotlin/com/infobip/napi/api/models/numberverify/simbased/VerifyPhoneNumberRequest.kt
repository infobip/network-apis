package com.infobip.napi.api.models.numberverify.simbased

import com.infobip.napi.api.ApiPaths
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class VerifyPhoneNumberRequest(
    @field:NotBlank
    val vpToken: String? = null,
    @field:NotBlank
    @field:Pattern(regexp = ApiPaths.MSISDN_PATTERN)
    val phoneNumber: String? = null,
) {
    override fun toString() = "VerifyPhoneNumberRequest(phoneNumber=$phoneNumber)"
}
