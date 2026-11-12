package com.infobip.napi.client

import com.infobip.napi.api.models.InfobipNapiError
import com.infobip.napi.client.models.InfobipSmvStatus
import com.infobip.napi.client.models.InfobipSmvVerifyRequest
import com.infobip.napi.client.models.InfobipSmvVerifyResponse
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
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.util.stream.Stream

private const val VERIFY_URL = "$TEST_INFOBIP_BASE_URL/mi/verification/1/verify"
private const val MSISDN = "385911234567"
private const val CALLBACK_URL = "https://demo.example.com/napi/verify/callback"
private const val DEVICE_IP = "1.2.3.4"
private const val DEVICE_PORT = 5678
private const val TOKEN = "smv-token"
private const val REDIRECT_URL = "https://mno.example.com/authorize"

class SilentMobileVerificationClientTest {

    private val restClientBuilder = InfobipClientConfig()
        .infobipRestClient(RestClient.builder(), testInfobipProperties())
        .mutate()

    private val server = MockRestServiceServer.bindTo(restClientBuilder).build()

    private val client = SilentMobileVerificationClient(restClientBuilder.build())

    private val request = InfobipSmvVerifyRequest(true, MSISDN, CALLBACK_URL, null, DEVICE_IP, DEVICE_PORT)

    @Test
    fun verify_should_PostRequestWithApiKeyAndReturnResponse_when_InfobipAccepts() {
        server.expect(requestTo(VERIFY_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, TEST_INFOBIP_AUTHORIZATION_HEADER_VALUE))
            .andExpect(
                content().json(
                    """{"consentGranted":true,"phoneNumber":"$MSISDN","callbackUrl":"$CALLBACK_URL","deviceIp":"$DEVICE_IP","devicePort":$DEVICE_PORT}"""
                )
            )
            .andRespond(
                withSuccess("""{"status":"REDIRECT","token":"$TOKEN","deviceRedirectUrl":"$REDIRECT_URL"}""", MediaType.APPLICATION_JSON)
            )

        val response = client.verify(request)

        assertThat(response).isEqualTo(InfobipSmvVerifyResponse(InfobipSmvStatus.REDIRECT, TOKEN, REDIRECT_URL, null))
        server.verify()
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 422])
    fun verify_should_ReturnMobileIdentityError_when_InfobipRejectsRequest(status: Int) {
        server.expect(requestTo(VERIFY_URL))
            .andRespond(
                withStatus(HttpStatus.valueOf(status))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"status":"ERROR","token":"$TOKEN","error":{"id":202,"name":"EC_CONSENT_NOT_GRANTED","description":"Consent not granted"}}""")
            )

        val response = client.verify(request)

        assertThat(response).isEqualTo(
            InfobipSmvVerifyResponse(
                InfobipSmvStatus.ERROR,
                TOKEN,
                null,
                InfobipNapiError(202, "EC_CONSENT_NOT_GRANTED", "Consent not granted")
            )
        )
    }

    @ParameterizedTest
    @MethodSource("failedResponses")
    fun verify_should_ThrowInfobipCallException_when_ResponseHasNoMobileIdentityError(
        status: HttpStatus,
        contentType: MediaType,
        body: String
    ) {
        server.expect(requestTo(VERIFY_URL))
            .andRespond(withStatus(status).contentType(contentType).body(body))

        assertThatThrownBy { client.verify(request) }
            .isInstanceOfSatisfying(InfobipCallException::class.java) {
                assertThat(it.status).isEqualTo(status)
            }
    }

    companion object {
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
