package com.infobip.napi.client.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfobipCamaraError(
    val status: Int? = null,
    val code: String? = null,
    val message: String? = null,
)

data class InfobipCamaraResult<T>(
    val correlator: String?,
    val body: T?,
    val error: InfobipCamaraError?,
)
