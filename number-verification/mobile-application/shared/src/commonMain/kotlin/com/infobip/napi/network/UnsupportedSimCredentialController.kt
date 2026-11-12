package com.infobip.napi.network

import com.infobip.napi.model.domain.SimCredentialError

class UnsupportedSimCredentialController : SimCredentialController {
    override val supportsSimCredential: Boolean = false
    override suspend fun activeCarrierIds(): List<Int> = emptyList()
    override suspend fun requestCredential(credentialRequest: String): String =
        throw SimCredentialException(SimCredentialError.UNSUPPORTED)
}
