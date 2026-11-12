package com.infobip.napi.service

import com.infobip.napi.config.TEST_INFOBIP_BASE_URL
import com.infobip.napi.config.testInfobipProperties
import com.infobip.napi.service.store.TEST_BACKEND_DOMAIN
import com.infobip.napi.service.store.testVerifyProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.web.util.UriComponentsBuilder

private const val MSISDN = "385911234567"

class VerifyUrlBuilderTest {

    private val urlBuilder = VerifyUrlBuilder(testVerifyProperties(), testInfobipProperties())

    @Test
    fun smvCallbackUrl_should_PointToEnterpriseBackendCallback_when_Called() {
        assertThat(urlBuilder.smvCallbackUrl()).isEqualTo("$TEST_BACKEND_DOMAIN/napi/verify/callback")
    }

    @Test
    fun camaraAuthorizeUrl_should_TargetInfobipAndEncodeQueryParameters_when_ClientIdContainsReservedCharacters() {
        val url = urlBuilder.camaraAuthorizeUrl("state-1", "client&redirect_url=https://evil.example.com", MSISDN)

        val components = UriComponentsBuilder.fromUriString(url).build(true)
        val queryParams = UriComponentsBuilder.fromUriString(url).build().queryParams

        assertThat(components.scheme + "://" + components.host + components.path)
            .isEqualTo("$TEST_INFOBIP_BASE_URL/camara/number-verification/v0/authorize")
        assertThat(queryParams["redirect_url"]).hasSize(1)
        assertThat(components.queryParams.getFirst("state")).isEqualTo("state-1")
        assertThat(components.queryParams.getFirst("client_id"))
            .isEqualTo("client%26redirect_url%3Dhttps%3A%2F%2Fevil.example.com")
        assertThat(components.queryParams.getFirst("redirect_url"))
            .isEqualTo("https%3A%2F%2Fbackend.example.com%2Fnapi%2Fverify%2Fcamara-authorize-complete")
        assertThat(components.queryParams.getFirst("login_hint")).isEqualTo("tel:%2B$MSISDN")
    }

    @ParameterizedTest
    @ValueSource(strings = [MSISDN, "+$MSISDN"])
    fun camaraAuthorizeUrl_should_AddTelLoginHintWithSinglePlusPrefix_when_MsisdnProvided(msisdn: String) {
        val url = urlBuilder.camaraAuthorizeUrl("state-1", "client-1", msisdn)

        val components = UriComponentsBuilder.fromUriString(url).build(true)

        assertThat(components.queryParams["login_hint"]).containsExactly("tel:%2B$MSISDN")
    }
}
