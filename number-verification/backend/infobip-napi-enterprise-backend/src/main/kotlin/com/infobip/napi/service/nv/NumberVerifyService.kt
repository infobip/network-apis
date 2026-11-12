package com.infobip.napi.service.nv

import com.infobip.napi.api.models.numberverify.ClientVerifyResponse
import com.infobip.napi.api.models.numberverify.VerifyStatus
import com.infobip.napi.client.NumberVerifyClient
import com.infobip.napi.client.models.InfobipCamaraResult
import com.infobip.napi.client.models.InfobipNumberVerifyResponse
import com.infobip.napi.service.VerifyUrlBuilder
import com.infobip.napi.service.store.AuthorizationStateStore
import com.infobip.napi.service.store.VerificationRequestDetails
import com.infobip.napi.service.store.VerificationResultStore
import com.infobip.napi.service.store.VerifyResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

private val log = LoggerFactory.getLogger(NumberVerifyService::class.java)

private const val DEFAULT_ERROR_DESCRIPTION = "Number verification failed"

@Service
class NumberVerifyService(
    private val authorizationStateStore: AuthorizationStateStore,
    private val verificationResultStore: VerificationResultStore,
    private val urlBuilder: VerifyUrlBuilder,
    private val numberVerifyClient: NumberVerifyClient
) {
    fun startVerification(msisdn: String, camaraClientId: String): ClientVerifyResponse {
        val state = UUID.randomUUID().toString()
        log.debug("NV started for state: {} and MSISDN: {}", state, msisdn)
        authorizationStateStore.create(state, VerificationRequestDetails(msisdn))
        verificationResultStore.registerPending(state)
        return ClientVerifyResponse(state, urlBuilder.camaraAuthorizeUrl(state, camaraClientId, msisdn))
    }

    fun completeAuthorization(state: String, authCode: String) {
        val verificationRequestDetails = consumeState(state) ?: return

        val result = try {
            toVerifyResult(state, numberVerifyClient.verify(authCode, verificationRequestDetails.msisdn))
        } catch (exception: Exception) {
            log.error("NV verify request failed for state: {}", state, exception)
            VerifyResult.error(state, DEFAULT_ERROR_DESCRIPTION)
        }

        log.debug("NV verify finished for state: {} with status: {}", state, result.status)
        verificationResultStore.complete(result)
    }

    fun failAuthorization(state: String, error: String?, errorDescription: String?) {
        consumeState(state) ?: return

        log.warn("NV authorization failed for state: {}, error: {}, description: {}", state, error, errorDescription)
        verificationResultStore.complete(VerifyResult.error(state, errorDescription ?: error ?: DEFAULT_ERROR_DESCRIPTION))
    }

    private fun consumeState(state: String): VerificationRequestDetails? =
        authorizationStateStore.consume(state).also {
            if (it == null) {
                log.warn("NV authorization state not found or already used: {}", state)
            }
        }

    private fun toVerifyResult(state: String, response: InfobipCamaraResult<InfobipNumberVerifyResponse>): VerifyResult =
        when (response.body?.devicePhoneNumberVerified) {
            true -> VerifyResult(state, VerifyStatus.VALID)
            false -> VerifyResult(state, VerifyStatus.INVALID)
            null -> VerifyResult.error(state, response.error?.message ?: response.error?.code ?: DEFAULT_ERROR_DESCRIPTION)
        }
}
