package com.infobip.napi.client

import com.infobip.napi.client.models.InfobipSmvVerifyRequest
import com.infobip.napi.client.models.InfobipSmvVerifyResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

private const val SMV_VERIFY_PATH = "/mi/verification/1/verify"

@Component
class SilentMobileVerificationClient(
    private val infobipRestClient: RestClient
) {
    fun verify(request: InfobipSmvVerifyRequest): InfobipSmvVerifyResponse =
        infobipRestClient.post()
            .uri(SMV_VERIFY_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchangeForRequiredValue { _, response ->
                response.bodyOrNull(typeReference<InfobipSmvVerifyResponse>())
                    ?.takeIf { response.statusCode.is2xxSuccessful || it.error != null }
                    ?: throw InfobipCallException(response.statusCode)
            }
}
