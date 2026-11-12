package com.infobip.napi

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Base64

private const val CLIENT_ID = "integration-client"
private const val CLIENT_SECRET = "integration-secret"

@SpringBootTest(
    properties = [
        "ENTERPRISE_BACKEND_DOMAIN=https://backend.example.com",
        "ENTERPRISE_AUTH_TOKEN_SECRET=integration-token-secret",
        "ENTERPRISE_CLIENT_ID=$CLIENT_ID",
        "ENTERPRISE_CLIENT_SECRET=$CLIENT_SECRET",
        "INFOBIP_BASE_URL=https://api.example.com",
        "INFOBIP_AUTHORIZATION_HEADER_VALUE=IBSSO integration-token"
    ]
)
@AutoConfigureMockMvc
class ApplicationIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun health_should_ReportUp_when_ApplicationStarted() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun token_should_IssueTokenAcceptedByProtectedEndpoint_when_ConfiguredClientAuthenticates() {
        val response = mockMvc.perform(tokenRequest(CLIENT_SECRET))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val accessToken = Regex("\"access_token\":\"([^\"]+)\"").find(response)!!.groupValues[1]

        mockMvc.perform(get("/napi/device-address").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.address").value("127.0.0.1"))
    }

    @Test
    fun token_should_ReturnUnauthorized_when_ClientSecretIsWrong() {
        mockMvc.perform(tokenRequest("wrong-secret"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("invalid_client"))
    }

    @Test
    fun deviceAddress_should_ReturnUnauthorized_when_AuthorizationMissingOrBasicCredentialsWrong() {
        mockMvc.perform(get("/napi/device-address"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/napi/device-address").header(HttpHeaders.AUTHORIZATION, basic("$CLIENT_ID:wrong-secret")))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun deviceAddress_should_Allow_when_BasicCredentialsMatchConfiguredClient() {
        mockMvc.perform(get("/napi/device-address").header(HttpHeaders.AUTHORIZATION, basic("$CLIENT_ID:$CLIENT_SECRET")))
            .andExpect(status().isOk)
    }

    @Test
    fun result_should_OmitNullFields_when_RequestIsRejected() {
        mockMvc.perform(get("/napi/verify/xyz/385911234567").header(HttpHeaders.AUTHORIZATION, basic("$CLIENT_ID:$CLIENT_SECRET")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorDescription").value("Invalid request: verificationType"))
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(jsonPath("$.result").doesNotExist())
    }

    private fun tokenRequest(clientSecret: String) =
        post("/napi/oauth2/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "client_credentials")
            .param("client_id", CLIENT_ID)
            .param("client_secret", clientSecret)

    private fun basic(credentials: String) = "Basic ${Base64.getEncoder().encodeToString(credentials.toByteArray())}"
}
