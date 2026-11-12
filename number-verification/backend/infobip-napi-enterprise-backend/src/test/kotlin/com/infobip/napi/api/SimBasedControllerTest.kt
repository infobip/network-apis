package com.infobip.napi.api

import com.infobip.napi.api.models.numberverify.simbased.DcqlResponse
import com.infobip.napi.api.models.numberverify.simbased.GetPhoneNumberResponse
import com.infobip.napi.api.models.numberverify.simbased.VerifyPhoneNumberResponse
import com.infobip.napi.service.RequestRejectedException
import com.infobip.napi.service.simbased.SimBasedService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val MSISDN = "385911234567"

@ExtendWith(MockitoExtension::class)
class SimBasedControllerTest {

    @Mock
    private lateinit var simBasedService: SimBasedService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(SimBasedController(simBasedService))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun verifyPhoneNumberDcql_should_ReturnCredentialRequest() {
        `when`(simBasedService.verifyPhoneNumberDcql(listOf(1, 2)))
            .thenReturn(DcqlResponse("n", 2, "{}"))

        mockMvc.perform(json(post("/napi/sim-based/verify/dcql"), """{"carrierIds":[1,2]}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nonce").value("n"))
            .andExpect(jsonPath("$.carrierId").value(2))
            .andExpect(jsonPath("$.credentialRequest").value("{}"))
    }

    @Test
    fun getPhoneNumberDcql_should_ReturnCredentialRequest() {
        `when`(simBasedService.getPhoneNumberDcql(listOf(1)))
            .thenReturn(DcqlResponse("n", 1, "{}"))

        mockMvc.perform(json(post("/napi/sim-based/device-phone-number/dcql"), """{"carrierIds":[1]}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.carrierId").value(1))
    }

    @Test
    fun verifyPhoneNumber_should_ReturnVerified() {
        `when`(simBasedService.verifyPhoneNumber("vp", MSISDN))
            .thenReturn(VerifyPhoneNumberResponse(true))

        mockMvc.perform(json(post("/napi/sim-based/verify"), """{"vpToken":"vp","phoneNumber":"$MSISDN"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verified").value(true))
    }

    @Test
    fun getPhoneNumber_should_ReturnPhoneNumber() {
        `when`(simBasedService.getPhoneNumber("vp"))
            .thenReturn(GetPhoneNumberResponse(MSISDN))

        mockMvc.perform(json(post("/napi/sim-based/device-phone-number"), """{"vpToken":"vp"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.phoneNumber").value(MSISDN))
    }

    @Test
    fun getPhoneNumber_should_Return400WithDescription_when_Rejected() {
        `when`(simBasedService.getPhoneNumber("vp"))
            .thenThrow(RequestRejectedException("Nope"))

        mockMvc.perform(json(post("/napi/sim-based/device-phone-number"), """{"vpToken":"vp"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorDescription").value("Nope"))
            .andExpect(jsonPath("$.token").doesNotExist())
    }

    @ParameterizedTest
    @ValueSource(strings = ["""{"carrierIds":[]}""", """{}"""])
    fun dcql_should_Return400_when_CarrierIdsEmptyOrMissing(body: String) {
        mockMvc.perform(json(post("/napi/sim-based/verify/dcql"), body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorDescription").value("Invalid request: carrierIds"))
    }

    @Test
    fun verifyPhoneNumber_should_Return400_when_PhoneNumberBad() {
        mockMvc.perform(json(post("/napi/sim-based/verify"), """{"vpToken":"vp","phoneNumber":"abc"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorDescription").value("Invalid request: phoneNumber"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["""{"vpToken":" "}""", """{}"""])
    fun getPhoneNumber_should_Return400_when_VpTokenBlankOrMissing(body: String) {
        mockMvc.perform(json(post("/napi/sim-based/device-phone-number"), body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorDescription").value("Invalid request: vpToken"))
    }

    @Test
    fun getPhoneNumber_should_Return400_when_JsonMalformed() {
        mockMvc.perform(json(post("/napi/sim-based/device-phone-number"), "{"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorDescription").value("Invalid request"))
    }

    private fun json(builder: MockHttpServletRequestBuilder, body: String): MockHttpServletRequestBuilder =
        builder
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
}
