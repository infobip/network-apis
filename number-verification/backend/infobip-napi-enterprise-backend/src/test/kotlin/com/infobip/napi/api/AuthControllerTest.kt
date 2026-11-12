package com.infobip.napi.api

import com.infobip.napi.api.models.auth.OAuthErrorResponse
import com.infobip.napi.service.auth.AccessToken
import com.infobip.napi.service.auth.IssuedAccessToken
import com.infobip.napi.service.auth.OAuthTokenResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val TOKEN_PATH = "/napi/oauth2/token"
private const val CLIENT_ID = "demo-client"
private const val CLIENT_SECRET = "demo-secret"
private const val ACCESS_TOKEN = "header.payload.signature"
private const val EXPIRES_IN = 3600L

@ExtendWith(MockitoExtension::class)
class AuthControllerTest {

    @Mock
    private lateinit var accessTokenService: AccessToken

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(AuthController(accessTokenService))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun token_should_IssueBearerToken_when_ClientCredentialsAreValid() {
        `when`(accessTokenService.getOauthToken(CLIENT_ID, CLIENT_SECRET))
            .thenReturn(OAuthTokenResult(IssuedAccessToken(ACCESS_TOKEN, "Bearer", EXPIRES_IN)))

        mockMvc.perform(tokenRequest())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.access_token").value(ACCESS_TOKEN))
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in").value(EXPIRES_IN))

        verify(accessTokenService).getOauthToken(CLIENT_ID, CLIENT_SECRET)
    }

    @ParameterizedTest
    @CsvSource(
        "invalid_client, Client authentication failed, 401",
        "invalid_request, client_id and client_secret are required, 400"
    )
    fun token_should_ReturnServiceError_when_TokenIsNotIssued(error: String, errorDescription: String, expectedStatus: Int) {
        `when`(accessTokenService.getOauthToken(CLIENT_ID, CLIENT_SECRET))
            .thenReturn(OAuthTokenResult(OAuthErrorResponse(error, errorDescription)))

        mockMvc.perform(tokenRequest())
            .andExpect(status().`is`(expectedStatus))
            .andExpect(jsonPath("$.error").value(error))
            .andExpect(jsonPath("$.error_description").value(errorDescription))

        verify(accessTokenService).getOauthToken(CLIENT_ID, CLIENT_SECRET)
    }

    @Test
    fun token_should_ReturnInvalidClient_when_ServiceFailsWithoutError() {
        `when`(accessTokenService.getOauthToken(CLIENT_ID, CLIENT_SECRET)).thenReturn(OAuthTokenResult(success = false))

        mockMvc.perform(tokenRequest())
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("invalid_client"))
            .andExpect(jsonPath("$.error_description").value("Client authentication failed"))
    }

    @ParameterizedTest
    @CsvSource(value = ["password", "authorization_code", "NULL"], nullValues = ["NULL"])
    fun token_should_ReturnUnsupportedGrantType_when_GrantTypeIsNotClientCredentials(grantType: String?) {
        mockMvc.perform(
            post(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .apply { grantType?.let { param("grant_type", it) } }
                .param("client_id", CLIENT_ID)
                .param("client_secret", CLIENT_SECRET)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("unsupported_grant_type"))

        verifyNoInteractions(accessTokenService)
    }

    @Test
    fun token_should_ReturnUnsupportedMediaType_when_BodyIsJson() {
        mockMvc.perform(
            post(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"grant_type":"client_credentials","client_id":"$CLIENT_ID","client_secret":"$CLIENT_SECRET"}""")
        )
            .andExpect(status().isUnsupportedMediaType)

        verifyNoInteractions(accessTokenService)
    }

    private fun tokenRequest() =
        post(TOKEN_PATH)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "client_credentials")
            .param("client_id", CLIENT_ID)
            .param("client_secret", CLIENT_SECRET)
}
