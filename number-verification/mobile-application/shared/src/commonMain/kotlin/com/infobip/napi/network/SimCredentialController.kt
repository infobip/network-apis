package com.infobip.napi.network

import com.infobip.napi.model.domain.SimCredentialError

interface SimCredentialController {
    val supportsSimCredential: Boolean
    suspend fun activeCarrierIds(): List<Int>

    /** Passes the backend's OpenID4VP request to the OS and returns the credential JSON it answers with. */
    suspend fun requestCredential(credentialRequest: String): String
}

class SimCredentialException(
    val error: SimCredentialError,
    message: String? = null,
    /**
     * What CredentialManager reported, shown as it is: its whole exception, or the credential JSON it returned
     * with an error instead of a `vp_token`. Null when the error did not come from it.
     */
    val credentialError: String? = null,
) : Exception(message ?: error.description)
