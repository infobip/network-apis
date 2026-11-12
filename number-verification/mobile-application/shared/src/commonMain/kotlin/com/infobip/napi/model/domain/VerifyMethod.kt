package com.infobip.napi.model.domain

/** Verification flow the user picks. SMV and CAMARA run as a [VerificationType]; SIM_BASED uses the `/sim-based` calls. */
enum class VerifyMethod {
    SMV,
    CAMARA,
    SIM_BASED,
}
