package com.infobip.napi.api.models.numberverify.simbased

import jakarta.validation.constraints.NotEmpty

data class DcqlRequest(
    @field:NotEmpty
    val carrierIds: List<Int>? = null,
)
