package com.infobip.napi.client

import com.infobip.napi.client.models.InfobipCamaraError
import com.infobip.napi.client.models.InfobipCamaraResult
import com.infobip.napi.client.models.InfobipDcqlClaim
import com.infobip.napi.client.models.InfobipDcqlCredential
import com.infobip.napi.client.models.InfobipDcqlMeta
import com.infobip.napi.client.models.InfobipDevicePhoneNumberResponse
import com.infobip.napi.client.models.InfobipNumberVerifyResponse
import com.infobip.napi.client.models.Ts43UseCase
import com.infobip.napi.config.InfobipClientConfig
import com.infobip.napi.config.TEST_INFOBIP_AUTHORIZATION_HEADER_VALUE
import com.infobip.napi.config.TEST_INFOBIP_BASE_URL
import com.infobip.napi.config.testInfobipProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseActions
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient
import java.util.stream.Stream

private const val MSISDN = "385911234567"
private const val E164_MSISDN = "+$MSISDN"
private const val AUTH_CODE = "mi-request-id"
private const val VP_TOKEN = "header.payload.signature~disclosure~kb.jwt.signature"
private const val CORRELATOR = "mi-correlator"
private const val NONCE = "nonce-1"
private const val NV_V0_VERIFY_URL = "$TEST_INFOBIP_BASE_URL/camara/number-verification/v0/verify"
private const val NV_V2_VERIFY_URL = "$TEST_INFOBIP_BASE_URL/camara/number-verification/v2/verify"
private const val NV_V2_DEVICE_PHONE_NUMBER_URL = "$TEST_INFOBIP_BASE_URL/camara/number-verification/v2/device-phone-number"
private const val FORBIDDEN_BODY = """{"status":403,"code":"PERMISSION_DENIED","message":"Client does not have sufficient permissions"}"""
private val FORBIDDEN_ERROR = InfobipCamaraError(403, "PERMISSION_DENIED", "Client does not have sufficient permissions")

class NumberVerifyClientTest {

    private val restClientBuilder = InfobipClientConfig()
        .infobipRestClient(RestClient.builder(), testInfobipProperties())
        .mutate()

    private val server = MockRestServiceServer.bindTo(restClientBuilder).build()

    private val client = NumberVerifyClient(restClientBuilder.build())

    @ParameterizedTest
    @ValueSource(strings = [MSISDN, E164_MSISDN])
    fun verify_should_SendCodeInXCorrelatorAndE164PhoneNumber_when_Called(phoneNumber: String) {
        expect(NV_V0_VERIFY_URL, HttpMethod.POST, AUTH_CODE)
            .andExpect(content().json("""{"phoneNumber":"$E164_MSISDN"}"""))
            .andRespond(json(HttpStatus.OK, """{"devicePhoneNumberVerified":true}"""))

        val result = client.verify(AUTH_CODE, phoneNumber)

        assertThat(result).isEqualTo(InfobipCamaraResult(CORRELATOR, InfobipNumberVerifyResponse(true), null))
        server.verify()
    }

    @Test
    fun verify_should_ReturnCamaraError_when_InfobipRejectsRequest() {
        expect(NV_V0_VERIFY_URL, HttpMethod.POST, AUTH_CODE)
            .andRespond(json(HttpStatus.FORBIDDEN, FORBIDDEN_BODY))

        val result = client.verify(AUTH_CODE, MSISDN)

        assertThat(result).isEqualTo(InfobipCamaraResult(CORRELATOR, null, FORBIDDEN_ERROR))
    }

    @Test
    fun verifyTs43_should_SendVpTokenInXCorrelatorAndReturnVerified_when_Called() {
        expect(NV_V2_VERIFY_URL, HttpMethod.POST, VP_TOKEN)
            .andExpect(content().json("""{"phoneNumber":"$E164_MSISDN"}"""))
            .andRespond(json(HttpStatus.OK, """{"devicePhoneNumberVerified":false}"""))

        val result = client.verifyTs43(VP_TOKEN, E164_MSISDN)

        assertThat(result).isEqualTo(InfobipCamaraResult(CORRELATOR, InfobipNumberVerifyResponse(false), null))
        server.verify()
    }

    @Test
    fun getPhoneNumber_should_SendVpTokenInXCorrelatorAndReturnPhoneNumber_when_Called() {
        expect(NV_V2_DEVICE_PHONE_NUMBER_URL, HttpMethod.GET, VP_TOKEN)
            .andRespond(json(HttpStatus.OK, """{"devicePhoneNumber":"$E164_MSISDN"}"""))

        val result = client.getPhoneNumber(VP_TOKEN)

        assertThat(result).isEqualTo(InfobipCamaraResult(CORRELATOR, InfobipDevicePhoneNumberResponse(E164_MSISDN), null))
        server.verify()
    }

    @Test
    fun getPhoneNumber_should_ReturnCamaraError_when_InfobipReturnsNotFound() {
        expect(NV_V2_DEVICE_PHONE_NUMBER_URL, HttpMethod.GET, VP_TOKEN)
            .andRespond(json(HttpStatus.NOT_FOUND, """{"status":404,"code":"NOT_FOUND","message":"Not found"}"""))

        val result = client.getPhoneNumber(VP_TOKEN)

        assertThat(result).isEqualTo(InfobipCamaraResult(CORRELATOR, null, InfobipCamaraError(404, "NOT_FOUND", "Not found")))
    }

    @ParameterizedTest
    @MethodSource("dcqlUseCases")
    fun getDcql_should_PostToUseCasePathAndParseCredentialsAndCarrierErrors_when_Called(useCase: Ts43UseCase, path: String) {
        server.expect(requestTo("$TEST_INFOBIP_BASE_URL$path"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, TEST_INFOBIP_AUTHORIZATION_HEADER_VALUE))
            .andExpect(headerDoesNotExist("X-Correlator"))
            .andExpect(content().json("""{"nonce":"$NONCE","carrierIds":[1234,5678]}"""))
            .andRespond(
                json(
                    HttpStatus.OK,
                    """
                    {
                      "1234": {
                        "id": "example-credential",
                        "format": "dc-authorization+sd-jwt",
                        "meta": {"credential_authorization_jwt": "eyJ.jwt.sig", "vct_values": ["number-verification/verify/ts43"]},
                        "claims": [{"path": ["android_carrier_hint"], "values": [1234]}]
                      },
                      "5678": {"status": 404, "code": "NOT_FOUND", "message": "Not found"}
                    }
                    """
                )
            )

        val result = client.getDcql(useCase, NONCE, listOf(1234, 5678))

        assertThat(result).isEqualTo(
            InfobipCamaraResult(
                CORRELATOR,
                mapOf(
                    "1234" to InfobipDcqlCredential(
                        id = "example-credential",
                        format = "dc-authorization+sd-jwt",
                        meta = InfobipDcqlMeta("eyJ.jwt.sig", listOf("number-verification/verify/ts43")),
                        claims = listOf(InfobipDcqlClaim(listOf("android_carrier_hint"), listOf(1234)))
                    ),
                    "5678" to InfobipDcqlCredential(status = 404, code = "NOT_FOUND", message = "Not found")
                ),
                null
            )
        )
        server.verify()
    }

    @ParameterizedTest
    @MethodSource("failedResponses")
    fun getPhoneNumber_should_ThrowInfobipCallException_when_ResponseIsNotCamaraError(
        status: HttpStatus,
        contentType: MediaType,
        body: String
    ) {
        server.expect(requestTo(NV_V2_DEVICE_PHONE_NUMBER_URL))
            .andRespond(withStatus(status).contentType(contentType).body(body))

        assertThatThrownBy { client.getPhoneNumber(VP_TOKEN) }
            .isInstanceOfSatisfying(InfobipCallException::class.java) {
                assertThat(it.status).isEqualTo(status)
            }
    }

    private fun expect(url: String, httpMethod: HttpMethod, correlator: String): ResponseActions =
        server.expect(requestTo(url))
            .andExpect(method(httpMethod))
            .andExpect(header(HttpHeaders.AUTHORIZATION, TEST_INFOBIP_AUTHORIZATION_HEADER_VALUE))
            .andExpect(header("X-Correlator", correlator))

    private fun json(status: HttpStatus, body: String) =
        withStatus(status)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Correlator", CORRELATOR)
            .body(body)

    companion object {
        @JvmStatic
        fun dcqlUseCases(): Stream<Arguments> = Stream.of(
            Arguments.of(Ts43UseCase.VERIFY_PHONE_NUMBER, "/camara/number-verification/v2/dcql/verify"),
            Arguments.of(Ts43UseCase.GET_PHONE_NUMBER, "/camara/number-verification/v2/dcql/device-phone-number"),
        )

        @JvmStatic
        fun failedResponses(): Stream<Arguments> = Stream.of(
            Arguments.of(
                HttpStatus.UNAUTHORIZED,
                MediaType.APPLICATION_JSON,
                """{"requestError":{"serviceException":{"messageId":"UNAUTHORIZED","text":"Invalid login details"}}}"""
            ),
            Arguments.of(HttpStatus.BAD_GATEWAY, MediaType.TEXT_HTML, "<html>Bad Gateway</html>"),
        )
    }
}
