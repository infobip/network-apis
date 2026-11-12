package com.infobip.napi.service.simbased

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.json.JsonMapper
import java.util.Base64

class VpTokenClaimsTest {

    private val jsonMapper = JsonMapper.builder().build()

    private fun b64(json: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    private fun jwt(payload: String) = "${b64("""{"alg":"ES256"}""")}.${b64(payload)}.signature"

    @Test
    fun parseVpTokenClaims_should_ReadNonceAndStateFromKeyBindingJwt() {
        val vpToken = "${jwt("""{"nonce":"issuer-nonce","state":"issuer-state"}""")}~disclosure~" +
            jwt("""{"nonce":"nonce-1","state":"mi-request-1","sd_hash":"h"}""")

        assertThat(parseVpTokenClaims(vpToken, jsonMapper)).isEqualTo(VpTokenClaims("nonce-1", "mi-request-1"))
    }

    @Test
    fun parseVpTokenClaims_should_ReturnNullFields_when_ClaimsMissingOrNotStrings() {
        val vpToken = "${jwt("{}")}~${jwt("""{"nonce":123}""")}"

        assertThat(parseVpTokenClaims(vpToken, jsonMapper)).isEqualTo(VpTokenClaims(null, null))
    }

    @ParameterizedTest
    @ValueSource(strings = ["no-separator", "issuer~", "issuer~not-a-jwt", "issuer~a.!!!.c", "issuer~a.bm90LWpzb24.c"])
    fun parseVpTokenClaims_should_ReturnNull_when_TokenUnparsable(vpToken: String) {
        assertThat(parseVpTokenClaims(vpToken, jsonMapper)).isNull()
    }
}
