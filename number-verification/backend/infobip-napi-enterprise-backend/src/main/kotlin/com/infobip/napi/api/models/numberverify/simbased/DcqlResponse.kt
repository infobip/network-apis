package com.infobip.napi.api.models.numberverify.simbased

import com.infobip.napi.utils.LogUtils

private val CREDENTIAL_AUTHORIZATION_JWT = Regex("(\"credential_authorization_jwt\"\\s*:\\s*\")([^\"]*)(\")")

data class DcqlResponse(
    val nonce: String,
    val carrierId: Int,
    val credentialRequest: String,
) {

    // credentialRequest contains the full credential authorization JWT. It is long, so toString uses this regex to shorten it in logs.
    //
    // It matches the JWT inside credentialRequest:
    //   ..."meta":{"credential_authorization_jwt":"eyJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJtaWMifQ.c2lnbmF0dXJl","vct_values":[...]}...
    // in three groups: (1) the key up to the opening quote, (2) the JWT, (3) the closing quote.
    // Only group 2 is shortened, so the logged JSON keeps its shape:
    //   before: "credential_authorization_jwt":"eyJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJtaWMifQ.c2lnbmF0dXJl"
    //   after:  "credential_authorization_jwt":"eyJhbGciOi...lnbmF0dXJl"
    override fun toString(): String {
        val shortenedCredentialRequest = CREDENTIAL_AUTHORIZATION_JWT.replace(credentialRequest) {
            val (prefix, jwt, suffix) = it.destructured
            prefix + LogUtils.abbreviate(jwt) + suffix
        }
        return "DcqlResponse(nonce=$nonce, carrierId=$carrierId, credentialRequest=$shortenedCredentialRequest)"
    }
}
