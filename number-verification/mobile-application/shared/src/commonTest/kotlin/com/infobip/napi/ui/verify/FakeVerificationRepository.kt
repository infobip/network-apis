package com.infobip.napi.ui.verify

import com.infobip.napi.model.domain.VerificationOutcome
import com.infobip.napi.model.domain.VerificationType
import com.infobip.napi.repository.VerificationRepository

class FakeVerificationRepository(
    private val outcome: VerificationOutcome,
) : VerificationRepository {

    var callCount = 0
    var lastType: VerificationType? = null
    var lastAuthorization: String? = null
    var lastDomain: String? = null

    override suspend fun verify(
        domain: String,
        msisdn: String,
        authorization: String,
        type: VerificationType,
        debugEnabled: Boolean,
    ): VerificationOutcome {
        callCount++
        lastType = type
        lastAuthorization = authorization
        lastDomain = domain
        return outcome
    }
}
