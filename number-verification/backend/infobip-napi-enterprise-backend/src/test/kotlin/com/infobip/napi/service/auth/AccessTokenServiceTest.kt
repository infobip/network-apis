package com.infobip.napi.service.auth

import com.infobip.napi.config.TEST_CLIENT_ID
import com.infobip.napi.config.TEST_CLIENT_SECRET
import com.infobip.napi.config.TEST_SECOND_CLIENT_ID
import com.infobip.napi.config.TEST_SECOND_CLIENT_SECRET
import com.infobip.napi.config.TEST_TOKEN_TTL
import com.infobip.napi.config.testAuthProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

private val NOW: Instant = Instant.parse("2026-01-01T10:00:00Z")

class AccessTokenServiceTest {

    private val jsonMapper = JsonMapper.builder().build()
    private val service = serviceAt(NOW)

    @ParameterizedTest
    @CsvSource(
        "$TEST_CLIENT_ID, $TEST_CLIENT_SECRET",
        "$TEST_SECOND_CLIENT_ID, $TEST_SECOND_CLIENT_SECRET"
    )
    fun getOauthToken_should_ReturnSignedBearerJwtWithClientIdAsSubject_when_CredentialsMatchConfiguredClient(clientId: String, clientSecret: String) {
        val result = service.getOauthToken(clientId, clientSecret)

        assertTrue(result.success)
        assertNull(result.error)
        val issued = result.issuedAccessToken!!
        assertEquals("Bearer", issued.accessTokenType)
        assertEquals(TEST_TOKEN_TTL.toSeconds(), issued.expiresInSeconds)
        val claims = jsonMapper.readTree(Base64.getUrlDecoder().decode(issued.accessToken.split(".")[1]))
        assertEquals(clientId, claims.path("sub").asString())
        assertEquals(NOW.epochSecond, claims.path("iat").asLong())
        assertEquals(NOW.plus(TEST_TOKEN_TTL).epochSecond, claims.path("exp").asLong())
        assertTrue(service.isValidBearer(issued.accessToken))
    }

    @ParameterizedTest
    @CsvSource(
        "other-client, $TEST_CLIENT_SECRET",
        "$TEST_CLIENT_ID, other-secret",
        "$TEST_CLIENT_ID, ${TEST_CLIENT_SECRET}x",
        "$TEST_CLIENT_SECRET, $TEST_CLIENT_ID",
        "$TEST_CLIENT_ID, $TEST_SECOND_CLIENT_SECRET",
        "$TEST_SECOND_CLIENT_ID, $TEST_CLIENT_SECRET"
    )
    fun getOauthToken_should_ReturnInvalidClient_when_CredentialsDoNotMatchConfiguredClient(clientId: String, clientSecret: String) {
        assertError(service.getOauthToken(clientId, clientSecret), "invalid_client", "Client authentication failed")
    }

    @ParameterizedTest
    @CsvSource(value = ["NULL, secret", "user, NULL", "' ', secret", "user, ' '"], nullValues = ["NULL"])
    fun getOauthToken_should_ReturnInvalidRequest_when_ClientIdOrSecretMissing(clientId: String?, clientSecret: String?) {
        assertError(service.getOauthToken(clientId, clientSecret), "invalid_request", "client_id and client_secret are required")
    }

    @Test
    fun isValidBearer_should_ReturnFalse_when_TokenExpired() {
        val token = issueToken()

        assertFalse(serviceAt(NOW.plus(TEST_TOKEN_TTL)).isValidBearer(token))
    }

    @Test
    fun isValidBearer_should_ReturnFalse_when_SignedWithDifferentSecret() {
        val token = issueToken()
        val otherService = AccessTokenService(
            testAuthProperties().copy(tokenSecret = "other-secret"),
            jsonMapper,
            Clock.fixed(NOW, ZoneOffset.UTC)
        )

        assertFalse(otherService.isValidBearer(token))
    }

    @Test
    fun isValidBearer_should_ReturnFalse_when_PayloadTampered() {
        val (header, _, signature) = issueToken().split(".")
        val forgedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"x","exp":${NOW.plusSeconds(999_999).epochSecond}}""".toByteArray())

        assertFalse(service.isValidBearer("$header.$forgedPayload.$signature"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "abc", "a.b", "a.b.c", "a.b.c.d", "!!.@@.##"])
    fun isValidBearer_should_ReturnFalse_when_TokenMalformed(token: String) {
        assertFalse(service.isValidBearer(token))
    }

    @ParameterizedTest
    @ValueSource(strings = ["$TEST_CLIENT_ID:$TEST_CLIENT_SECRET", "$TEST_SECOND_CLIENT_ID:$TEST_SECOND_CLIENT_SECRET"])
    fun isValidBasic_should_ReturnTrue_when_CredentialsMatchConfiguredClient(credentials: String) {
        assertTrue(service.isValidBasic(basic(credentials)))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "user:", ":password", "no-separator", ":", "$TEST_CLIENT_ID:other-secret", "other-client:$TEST_CLIENT_SECRET",
            "$TEST_CLIENT_ID:$TEST_SECOND_CLIENT_SECRET", "$TEST_SECOND_CLIENT_ID:$TEST_CLIENT_SECRET"
        ]
    )
    fun isValidBasic_should_ReturnFalse_when_CredentialsIncompleteOrDoNotMatchConfiguredClient(credentials: String) {
        assertFalse(service.isValidBasic(basic(credentials)))
    }

    @Test
    fun isValidBasic_should_ReturnFalse_when_CredentialsAreNotBase64() {
        assertFalse(service.isValidBasic("not-base64!"))
    }

    private fun issueToken() = service.getOauthToken(TEST_CLIENT_ID, TEST_CLIENT_SECRET).issuedAccessToken!!.accessToken

    private fun assertError(result: OAuthTokenResult, expectedError: String, expectedDescription: String) {
        assertFalse(result.success)
        assertNull(result.issuedAccessToken)
        assertEquals(expectedError, result.error?.error)
        assertEquals(expectedDescription, result.error?.errorDescription)
    }

    private fun basic(credentials: String) = Base64.getEncoder().encodeToString(credentials.toByteArray())

    private fun serviceAt(instant: Instant) =
        AccessTokenService(testAuthProperties(), jsonMapper, Clock.fixed(instant, ZoneOffset.UTC))
}
