package com.infobip.napi.repository

import com.infobip.napi.network.SimCredentialController

class FakeSimCredentialController(
    override val supportsSimCredential: Boolean = true,
    private val carrierIds: List<Int> = listOf(1234),
    private val credentialJson: String = "",
    private val failure: Throwable? = null,
) : SimCredentialController {

    var carrierIdsCount = 0
    var requestCount = 0
    var lastCredentialRequest: String? = null

    override suspend fun activeCarrierIds(): List<Int> {
        carrierIdsCount++
        return carrierIds
    }

    override suspend fun requestCredential(credentialRequest: String): String {
        requestCount++
        lastCredentialRequest = credentialRequest
        failure?.let { throw it }
        return credentialJson
    }
}
