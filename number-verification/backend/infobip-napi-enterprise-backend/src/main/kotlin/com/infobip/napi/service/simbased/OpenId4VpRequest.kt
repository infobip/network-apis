package com.infobip.napi.service.simbased

import com.fasterxml.jackson.annotation.JsonProperty

data class OpenId4VpRequest(val requests: List<OpenId4VpProtocolRequest>)

data class OpenId4VpProtocolRequest(val protocol: String, val data: OpenId4VpData)

data class OpenId4VpData(
    @get:JsonProperty("response_type") val responseType: String,
    @get:JsonProperty("response_mode") val responseMode: String,
    val nonce: String,
    @get:JsonProperty("dcql_query") val dcqlQuery: DcqlQuery,
)

data class DcqlQuery(val credentials: List<DcqlCredential>)

data class DcqlCredential(
    val id: String?,
    val format: String?,
    val meta: DcqlCredentialMeta,
    val claims: List<DcqlCredentialClaim>?,
)

data class DcqlCredentialMeta(
    @get:JsonProperty("credential_authorization_jwt") val credentialAuthorizationJwt: String,
    @get:JsonProperty("vct_values") val vctValues: List<String>?,
)

data class DcqlCredentialClaim(val path: List<String>?, val values: List<Int>?)
