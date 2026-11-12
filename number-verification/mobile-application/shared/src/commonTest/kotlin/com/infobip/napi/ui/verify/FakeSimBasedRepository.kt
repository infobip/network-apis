package com.infobip.napi.ui.verify

import com.infobip.napi.model.domain.VerificationOutcome
import com.infobip.napi.repository.SimBasedRepository

class FakeSimBasedRepository(
    private val outcome: VerificationOutcome,
) : SimBasedRepository {

    var verifyCount = 0
    var getPhoneNumberCount = 0
    var lastMsisdn: String? = null
    var lastAuthorization: String? = null
    var lastDomain: String? = null

    override suspend fun verifyPhoneNumber(domain: String, msisdn: String, authorization: String): VerificationOutcome {
        verifyCount++
        lastMsisdn = msisdn
        lastAuthorization = authorization
        lastDomain = domain
        return outcome
    }

    override suspend fun getPhoneNumber(domain: String, authorization: String): VerificationOutcome {
        getPhoneNumberCount++
        lastAuthorization = authorization
        lastDomain = domain
        return outcome
    }
}
