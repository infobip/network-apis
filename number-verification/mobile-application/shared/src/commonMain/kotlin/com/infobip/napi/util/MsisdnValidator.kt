package com.infobip.napi.util

/**
 * Accepts an MSISDN in international format, matching the backend: an optional leading `+`
 * followed by 7 to 15 digits, the first of which is not zero.
 */
object MsisdnValidator {
    private val pattern = Regex("^\\+?[1-9][0-9]{6,14}$")

    fun validate(value: String?): String? = when {
        value.isNullOrEmpty() -> "Required"
        !pattern.matches(value) -> "Invalid MSISDN format"
        else -> null
    }
}
