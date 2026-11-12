package com.infobip.napi.api.models.numberverify.simbased

import jakarta.validation.constraints.NotBlank

data class GetPhoneNumberRequest(
    @field:NotBlank
    val vpToken: String? = null,
) {
    override fun toString() = "GetPhoneNumberRequest()"
}
