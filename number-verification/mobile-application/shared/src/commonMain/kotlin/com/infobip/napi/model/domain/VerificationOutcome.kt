package com.infobip.napi.model.domain

sealed interface VerificationOutcome {
    val msisdn: String
    val token: String?
    val deviceIp: String?
    val devicePort: Int?
    val nonce: String?

    data class Valid(
        override val msisdn: String,
        override val token: String? = null,
        override val deviceIp: String? = null,
        override val devicePort: Int? = null,
        override val nonce: String? = null,
    ) : VerificationOutcome

    data class Invalid(
        override val msisdn: String,
        override val token: String? = null,
        override val deviceIp: String? = null,
        override val devicePort: Int? = null,
        override val nonce: String? = null,
    ) : VerificationOutcome

    data class PhoneNumber(
        override val msisdn: String,
        override val token: String? = null,
        override val deviceIp: String? = null,
        override val devicePort: Int? = null,
        override val nonce: String? = null,
    ) : VerificationOutcome

    data class Error(
        override val msisdn: String,
        val message: String,
        override val token: String? = null,
        override val deviceIp: String? = null,
        override val devicePort: Int? = null,
        val additionalError: String? = null,
        override val nonce: String? = null,
    ) : VerificationOutcome
}
