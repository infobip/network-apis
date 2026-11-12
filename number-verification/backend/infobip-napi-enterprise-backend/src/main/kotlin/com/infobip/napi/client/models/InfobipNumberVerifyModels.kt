package com.infobip.napi.client.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

data class InfobipPhoneNumberRequest(val phoneNumber: String)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfobipNumberVerifyResponse(val devicePhoneNumberVerified: Boolean? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfobipDevicePhoneNumberResponse(val devicePhoneNumber: String? = null)

data class InfobipDcqlRequest(val nonce: String, val carrierIds: List<Int>)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfobipDcqlCredential(
    val id: String? = null,
    val format: String? = null,
    val meta: InfobipDcqlMeta? = null,
    val claims: List<InfobipDcqlClaim>? = null,
    val status: Int? = null,
    val code: String? = null,
    val message: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfobipDcqlMeta(
    @get:JsonProperty("credential_authorization_jwt") val credentialAuthorizationJwt: String? = null,
    @get:JsonProperty("vct_values") val vctValues: List<String>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfobipDcqlClaim(
    val path: List<String>? = null,
    val values: List<Int>? = null,
)

enum class Ts43UseCase {
    VERIFY_PHONE_NUMBER,
    GET_PHONE_NUMBER,
}
