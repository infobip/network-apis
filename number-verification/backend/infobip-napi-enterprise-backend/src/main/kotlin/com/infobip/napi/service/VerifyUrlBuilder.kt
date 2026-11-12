package com.infobip.napi.service

import com.infobip.napi.api.ApiPaths
import com.infobip.napi.config.InfobipProperties
import com.infobip.napi.config.VerifyProperties
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

private const val INFOBIP_NV_AUTHORIZE_PATH = "/camara/number-verification/v0/authorize"

@Component
class VerifyUrlBuilder(
    private val verifyProperties: VerifyProperties,
    private val infobipProperties: InfobipProperties
) {
    fun smvCallbackUrl(): String = domainUrl(ApiPaths.VERIFY_PREFIX + ApiPaths.CALLBACK).toUriString()

    fun camaraAuthorizeUrl(state: String, clientId: String, msisdn: String): String =
        UriComponentsBuilder.fromUriString(infobipProperties.baseUrl)
            .path(INFOBIP_NV_AUTHORIZE_PATH)
            .queryParam("state", "{state}")
            .queryParam("client_id", "{clientId}")
            .queryParam("redirect_url", "{redirectUrl}")
            .queryParam("login_hint", "tel:{msisdn}")
            .encode()
            .buildAndExpand(
                state,
                clientId,
                domainUrl(ApiPaths.VERIFY_PREFIX + ApiPaths.AUTHORIZE_COMPLETE).toUriString(),
                if (msisdn.startsWith("+")) msisdn else "+$msisdn"
            ).toUriString()

    private fun domainUrl(path: String): UriComponentsBuilder =
        UriComponentsBuilder.fromUriString(verifyProperties.domain)
            .path(path)
}
