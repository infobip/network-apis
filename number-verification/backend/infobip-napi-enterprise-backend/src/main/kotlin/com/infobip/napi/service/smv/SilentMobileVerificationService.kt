package com.infobip.napi.service.smv

import com.infobip.napi.api.models.numberverify.CallbackRequest
import com.infobip.napi.api.models.numberverify.ClientVerifyResponse
import com.infobip.napi.api.models.numberverify.VerifyStatus
import com.infobip.napi.client.SilentMobileVerificationClient
import com.infobip.napi.client.models.InfobipSmvStatus
import com.infobip.napi.client.models.InfobipSmvVerifyRequest
import com.infobip.napi.service.RequestRejectedException
import com.infobip.napi.service.VerifyUrlBuilder
import com.infobip.napi.service.store.VerificationResultStore
import com.infobip.napi.service.store.VerifyResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

private val log = LoggerFactory.getLogger(SilentMobileVerificationService::class.java)

private const val DEFAULT_ERROR_DESCRIPTION = "Silent mobile verification failed"

@Service
class SilentMobileVerificationService(
    private val resultStore: VerificationResultStore,
    private val urlBuilder: VerifyUrlBuilder,
    private val smvClient: SilentMobileVerificationClient
) {
    fun startVerification(msisdn: String, deviceIp: String, devicePort: Int): ClientVerifyResponse {
        log.debug("Sending SMV verify request to Infobip for MSISDN: {}", msisdn)
        val smvResponse = smvClient.verify(
            InfobipSmvVerifyRequest(
                consentGranted = true,
                phoneNumber = msisdn,
                callbackUrl = urlBuilder.smvCallbackUrl(),
                returnUrl = null,
                deviceIp = deviceIp,
                devicePort = devicePort
            )
        )

        val token = smvResponse.token
        if (token == null || (smvResponse.status != InfobipSmvStatus.OK && smvResponse.status != InfobipSmvStatus.REDIRECT)) {
            val error = smvResponse.error
            log.warn("SMV verify rejected for MSISDN: {}, status: {}, error: {} {}", msisdn, smvResponse.status, error?.id, error?.name)
            throw RequestRejectedException(error?.description ?: error?.name ?: DEFAULT_ERROR_DESCRIPTION, token)
        }

        // In case of SMV the Infobip token is taken and used as state parameter, in NV there is no Infobip token so NV generates its own state
        resultStore.registerPending(token)
        log.debug("SMV started for MSISDN: {}, token: {}, status: {}", msisdn, token, smvResponse.status)

        return ClientVerifyResponse(token, smvResponse.deviceRedirectUrl)
    }

    fun completeFromCallback(callbackRequest: CallbackRequest) {
        val result = callbackRequest.result
            ?.takeIf { it != VerifyStatus.ERROR }
            ?.let { VerifyResult(callbackRequest.token, it) }
            ?: VerifyResult.error(
                callbackRequest.token,
                callbackRequest.error?.description ?: callbackRequest.error?.name ?: DEFAULT_ERROR_DESCRIPTION
            )

        resultStore.complete(result)
    }
}
