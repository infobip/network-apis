package com.infobip.napi.api

import com.infobip.napi.api.models.InfobipNapiError
import com.infobip.napi.api.models.numberverify.CallbackRequest
import com.infobip.napi.api.models.numberverify.ClientVerifyResponse
import com.infobip.napi.api.models.numberverify.VerifyStatus
import com.infobip.napi.service.RequestRejectedException
import com.infobip.napi.service.nv.NumberVerifyService
import com.infobip.napi.service.smv.SilentMobileVerificationService
import com.infobip.napi.service.store.VerificationResultStore
import com.infobip.napi.service.store.VerifyResult
import com.infobip.napi.service.store.testVerifyProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val MSISDN = "385911234567"
private const val TOKEN = "token-1"
private const val STATE = "state-1"
private const val CLIENT_ID = "client-1"
private const val REMOTE_ADDRESS = "127.0.0.1"
private const val REDIRECT_URL = "https://operator.example.com/redirect"
private const val VERIFY_PATH = "/napi/verify/{verificationType}/{msisdn}"

@ExtendWith(MockitoExtension::class)
class VerifyControllerTest {

    @Mock
    private lateinit var silentMobileVerificationService: SilentMobileVerificationService

    @Mock
    private lateinit var numberVerifyService: NumberVerifyService

    @Mock
    private lateinit var resultStore: VerificationResultStore

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = mockMvc(CLIENT_ID)
    }

    @Test
    fun verify_should_StartSilentMobileVerification_when_VerificationTypeIsSmv() {
        `when`(
            silentMobileVerificationService.startVerification(MSISDN, REMOTE_ADDRESS, 80)
        ).thenReturn(ClientVerifyResponse(TOKEN, REDIRECT_URL))

        mockMvc.perform(get(VERIFY_PATH, "smv", MSISDN).with { it.remotePort = 80; it })
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value(TOKEN))
            .andExpect(jsonPath("$.deviceRedirectUrl").value(REDIRECT_URL))

        verifyNoInteractions(numberVerifyService)
    }

    @Test
    fun verify_should_StartNumberVerification_when_VerificationTypeIsNv() {
        `when`(
            numberVerifyService.startVerification(MSISDN, CLIENT_ID)
        ).thenReturn(ClientVerifyResponse(STATE, REDIRECT_URL))

        mockMvc.perform(get(VERIFY_PATH, "nv", MSISDN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value(STATE))

        verifyNoInteractions(silentMobileVerificationService)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "   "])
    fun verify_should_ReturnJsonBadRequest_when_CamaraClientIdIsNotConfigured(camaraClientId: String?) {
        mockMvc(camaraClientId).perform(get(VERIFY_PATH, "nv", MSISDN))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errorDescription").value("Camara client ID is not configured for Camara Number Verify"))

        verifyNoInteractions(numberVerifyService, silentMobileVerificationService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["abc", "0123456789", "1234", "+1234", "3859112345678901"])
    fun verify_should_ReturnBadRequest_when_MsisdnIsInvalid(msisdn: String) {
        mockMvc.perform(get(VERIFY_PATH, "smv", msisdn))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorDescription").value("Invalid request: msisdn"))

        verifyNoInteractions(numberVerifyService, silentMobileVerificationService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["12345", "+12345", "385911234567890", "+385911234567890"])
    fun verify_should_StartNumberVerification_when_MsisdnLengthIsWithinBounds(msisdn: String) {
        `when`(
            numberVerifyService.startVerification(msisdn, CLIENT_ID)
        ).thenReturn(ClientVerifyResponse(STATE, REDIRECT_URL))

        mockMvc.perform(get(VERIFY_PATH, "nv", msisdn))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value(STATE))

        verifyNoInteractions(silentMobileVerificationService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["xyz", "NV", "smvx", "xnv", "nvsmv"])
    fun verify_should_ReturnBadRequest_when_VerificationTypeIsInvalid(verificationType: String) {
        mockMvc.perform(get(VERIFY_PATH, verificationType, MSISDN))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorDescription").value("Invalid request: verificationType"))

        verifyNoInteractions(numberVerifyService, silentMobileVerificationService)
    }

    @Test
    fun verify_should_ReturnRejectionDescriptionAndToken_when_InfobipRejectsRequest() {
        `when`(
            silentMobileVerificationService.startVerification(MSISDN, REMOTE_ADDRESS, 80)
        ).thenThrow(RequestRejectedException("No coverage", TOKEN))

        mockMvc.perform(get(VERIFY_PATH, "smv", MSISDN).with { it.remotePort = 80; it })
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorDescription").value("No coverage"))
            .andExpect(jsonPath("$.token").value(TOKEN))
    }

    @Test
    fun result_should_ReturnNoContent_when_ResultNotAvailable() {
        `when`(resultStore.find(TOKEN)).thenReturn(null)

        mockMvc.perform(get("/napi/verify/result/{token}", TOKEN))
            .andExpect(status().isNoContent)
    }

    @Test
    fun result_should_ReturnResult_when_ResultAvailable() {
        `when`(resultStore.find(TOKEN)).thenReturn(VerifyResult.error(TOKEN, "Device timed out"))

        mockMvc.perform(get("/napi/verify/result/{token}", TOKEN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value(TOKEN))
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.errorDescription").value("Device timed out"))
    }

    @Test
    fun callback_should_DeserializeInfobipPayloadAndComplete_when_ErrorDescriptionIsNull() {
        mockMvc.perform(
            post("/napi/verify/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$TOKEN","result":"ERROR","error":{"id":305,"name":"MOBILE_DEVICE_TIMEOUT","description":null}}""")
        )
            .andExpect(status().isOk)

        verify(silentMobileVerificationService).completeFromCallback(
            CallbackRequest(TOKEN, VerifyStatus.ERROR, InfobipNapiError(305, "MOBILE_DEVICE_TIMEOUT", null))
        )
    }

    @Test
    fun callback_should_DeserializeInfobipPayload_when_ResultIsValidAndErrorIsNull() {
        mockMvc.perform(
            post("/napi/verify/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$TOKEN","result":"VALID","error":null}""")
        )
            .andExpect(status().isOk)

        verify(silentMobileVerificationService).completeFromCallback(CallbackRequest(TOKEN, VerifyStatus.VALID, null))
    }

    @Test
    fun callback_should_ReturnJsonBadRequest_when_TokenIsBlank() {
        mockMvc.perform(
            post("/napi/verify/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":" ","result":"VALID","error":null}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorDescription").value("Invalid request: token"))
    }

    @Test
    fun authorizeComplete_should_CompleteAuthorizationAndReturnNoContent_when_CodeProvided() {
        mockMvc.perform(get("/napi/verify/camara-authorize-complete").param("state", STATE).param("code", "auth-code"))
            .andExpect(status().isNoContent)

        verify(numberVerifyService).completeAuthorization(STATE, "auth-code")
    }

    @Test
    fun authorizeComplete_should_FailAuthorizationAndReturnUnprocessable_when_ErrorProvidedWithoutCode() {
        mockMvc.perform(
            get("/napi/verify/camara-authorize-complete")
                .param("state", STATE)
                .param("error", "access_denied")
                .param("error_description", "User denied")
        )
            .andExpect(status().isUnprocessableContent)

        verify(numberVerifyService).failAuthorization(STATE, "access_denied", "User denied")
    }

    @Test
    fun authorizeComplete_should_FailAuthorization_when_NeitherCodeNorErrorProvided() {
        mockMvc.perform(get("/napi/verify/camara-authorize-complete").param("state", STATE))
            .andExpect(status().isUnprocessableContent)

        verify(numberVerifyService).failAuthorization(STATE, null, null)
    }

    private fun mockMvc(camaraClientId: String?): MockMvc {
        val controller = VerifyController(
            silentMobileVerificationService,
            numberVerifyService,
            resultStore,
            testVerifyProperties(camaraClientId = camaraClientId)
        )
        return MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }
}
