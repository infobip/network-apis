package com.infobip.napi.api.auth

import com.infobip.napi.config.WebConfig
import com.infobip.napi.service.auth.AccessToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.handler.MappedInterceptor
import org.springframework.web.util.ServletRequestPathUtils
import tools.jackson.databind.json.JsonMapper
import java.util.Base64

private const val VALID_TOKEN = "valid.jwt.token"
private const val INVALID_TOKEN = "invalid.jwt.token"
private const val CLIENT_ID = "demo-client"
private const val CLIENT_SECRET = "demo-secret"

@ExtendWith(MockitoExtension::class)
class AuthorizationInterceptorTest {

    @Mock
    private lateinit var accessTokenService: AccessToken

    private val jsonMapper = JsonMapper.builder().build()

    @Test
    fun preHandle_should_Allow_when_BearerTokenIsValid() {
        `when`(accessTokenService.isValidBearer(VALID_TOKEN)).thenReturn(true)

        assertTrue(preHandle("Bearer $VALID_TOKEN").first)
    }

    @Test
    fun preHandle_should_RejectWithUnauthorized_when_BearerTokenIsInvalid() {
        `when`(accessTokenService.isValidBearer(INVALID_TOKEN)).thenReturn(false)

        val (allowed, response) = preHandle("Bearer $INVALID_TOKEN")

        assertUnauthorized(allowed, response)
    }

    @Test
    fun preHandle_should_Allow_when_BasicCredentialsAreValid() {
        val credentials = basic("$CLIENT_ID:$CLIENT_SECRET")
        `when`(accessTokenService.isValidBasic(credentials)).thenReturn(true)

        assertTrue(preHandle("Basic $credentials").first)
    }

    @Test
    fun preHandle_should_RejectWithUnauthorized_when_BasicCredentialsAreInvalid() {
        val credentials = basic("$CLIENT_ID:wrong-secret")
        `when`(accessTokenService.isValidBasic(credentials)).thenReturn(false)

        val (allowed, response) = preHandle("Basic $credentials")

        assertUnauthorized(allowed, response)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "Bearer", "Bearer ", "Basic", "Basic ", "Digest abc"])
    fun preHandle_should_RejectWithUnauthorized_when_AuthorizationHeaderIsUnsupported(authorization: String) {
        val (allowed, response) = preHandle(authorization.ifEmpty { null })

        assertUnauthorized(allowed, response)
        verifyNoInteractions(accessTokenService)
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["/napi/oauth2/token", "/napi/verify/callback", "/napi/verify/camara-authorize-complete"]
    )
    fun addInterceptors_should_SkipAuthorization_when_PathIsPublic(path: String) {
        assertFalse(mappedInterceptor().matches(requestTo(path)))
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["/napi/device-address", "/napi/verify/smv/385911234567", "/napi/verify/result/t", "/napi/sim-based/verify"]
    )
    fun addInterceptors_should_RequireAuthorization_when_PathIsProtected(path: String) {
        assertTrue(mappedInterceptor().matches(requestTo(path)))
    }

    private fun preHandle(authorization: String?): Pair<Boolean, MockHttpServletResponse> {
        val request = MockHttpServletRequest("GET", "/napi/device-address")
        authorization?.let { request.addHeader(HttpHeaders.AUTHORIZATION, it) }
        val response = MockHttpServletResponse()
        return AuthorizationInterceptor(accessTokenService, jsonMapper).preHandle(request, response, Any()) to response
    }

    private fun assertUnauthorized(allowed: Boolean, response: MockHttpServletResponse) {
        assertFalse(allowed)
        assertEquals(401, response.status)
        assertEquals("Bearer", response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
        assertEquals("invalid_token", jsonMapper.readTree(response.contentAsString).path("error").asString())
    }

    private fun mappedInterceptor(): MappedInterceptor {
        val registry = object : InterceptorRegistry() {
            fun mapped() = interceptors.single() as MappedInterceptor
        }
        WebConfig(AuthorizationInterceptor(accessTokenService, jsonMapper)).addInterceptors(registry)
        return registry.mapped()
    }

    private fun requestTo(path: String) =
        MockHttpServletRequest("GET", path).also { ServletRequestPathUtils.parseAndCache(it) }

    private fun basic(credentials: String) = Base64.getEncoder().encodeToString(credentials.toByteArray())
}
