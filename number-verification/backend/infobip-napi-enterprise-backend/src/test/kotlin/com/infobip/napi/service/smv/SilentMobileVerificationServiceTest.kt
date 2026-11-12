package com.infobip.napi.service.smv

import com.infobip.napi.api.models.InfobipNapiError
import com.infobip.napi.api.models.numberverify.CallbackRequest
import com.infobip.napi.api.models.numberverify.ClientVerifyResponse
import com.infobip.napi.api.models.numberverify.VerifyStatus
import com.infobip.napi.client.SilentMobileVerificationClient
import com.infobip.napi.client.models.InfobipSmvStatus
import com.infobip.napi.client.models.InfobipSmvVerifyRequest
import com.infobip.napi.client.models.InfobipSmvVerifyResponse
import com.infobip.napi.service.RequestRejectedException
import com.infobip.napi.service.VerifyUrlBuilder
import com.infobip.napi.service.store.VerificationResultStore
import com.infobip.napi.service.store.VerifyResult
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.util.stream.Stream

private const val MSISDN = "385911234567"
private const val DEVICE_IP = "1.2.3.4"
private const val DEVICE_PORT = 5678
private const val CALLBACK_URL = "https://demo.example.com/napi/verify/callback"
private const val TOKEN = "smv-token"
private const val REDIRECT_URL = "https://operator.example.com/redirect"
private val SMV_REQUEST = InfobipSmvVerifyRequest(true, MSISDN, CALLBACK_URL, null, DEVICE_IP, DEVICE_PORT)

@ExtendWith(MockitoExtension::class)
class SilentMobileVerificationServiceTest {

    @Mock
    private lateinit var resultStore: VerificationResultStore

    @Mock
    private lateinit var urlBuilder: VerifyUrlBuilder

    @Mock
    private lateinit var smvClient: SilentMobileVerificationClient

    @InjectMocks
    private lateinit var service: SilentMobileVerificationService

    @ParameterizedTest
    @EnumSource(value = InfobipSmvStatus::class, names = ["OK", "REDIRECT"])
    fun startVerification_should_RegisterPendingAndReturnResponse_when_InfobipAcceptsRequest(status: InfobipSmvStatus) {
        `when`(urlBuilder.smvCallbackUrl()).thenReturn(CALLBACK_URL)
        `when`(smvClient.verify(SMV_REQUEST)).thenReturn(InfobipSmvVerifyResponse(status, TOKEN, REDIRECT_URL, null))

        val response = startVerification()

        assertThat(response).isEqualTo(ClientVerifyResponse(TOKEN, REDIRECT_URL))
        val inOrder = inOrder(urlBuilder, smvClient, resultStore)
        inOrder.verify(urlBuilder).smvCallbackUrl()
        inOrder.verify(smvClient).verify(SMV_REQUEST)
        inOrder.verify(resultStore).registerPending(TOKEN)
    }

    @ParameterizedTest
    @MethodSource("rejectedResponses")
    fun startVerification_should_ThrowRejectedAndNotRegister_when_InfobipRejectsRequest(
        smvResponse: InfobipSmvVerifyResponse,
        expectedDescription: String
    ) {
        `when`(urlBuilder.smvCallbackUrl()).thenReturn(CALLBACK_URL)
        `when`(smvClient.verify(SMV_REQUEST)).thenReturn(smvResponse)

        assertThatThrownBy { startVerification() }
            .isInstanceOfSatisfying(RequestRejectedException::class.java) {
                assertThat(it.description).isEqualTo(expectedDescription)
                assertThat(it.token).isEqualTo(smvResponse.token)
            }
        verifyNoInteractions(resultStore)
    }

    @Test
    fun startVerification_should_PropagateExceptionAndNotRegister_when_InfobipCallFails() {
        val failure = IllegalStateException("Infobip unavailable")
        `when`(urlBuilder.smvCallbackUrl()).thenReturn(CALLBACK_URL)
        `when`(smvClient.verify(SMV_REQUEST)).thenThrow(failure)

        assertThatThrownBy { startVerification() }.isSameAs(failure)
        verifyNoInteractions(resultStore)
    }

    @ParameterizedTest
    @MethodSource("callbacks")
    fun completeFromCallback_should_StoreMappedResult_when_CallbackReceived(
        callbackRequest: CallbackRequest,
        expected: VerifyResult
    ) {
        service.completeFromCallback(callbackRequest)

        verify(resultStore).complete(expected)
        verifyNoInteractions(smvClient, urlBuilder)
    }

    private fun startVerification() = service.startVerification(MSISDN, DEVICE_IP, DEVICE_PORT)

    companion object {
        @JvmStatic
        fun rejectedResponses(): Stream<Arguments> = Stream.of(
            Arguments.of(
                InfobipSmvVerifyResponse(
                    InfobipSmvStatus.ERROR, TOKEN, null, InfobipNapiError(101, "GATEWAY_OPERATION_FAILED", "Gateway failed")
                ),
                "Gateway failed"
            ),
            Arguments.of(
                InfobipSmvVerifyResponse(InfobipSmvStatus.ERROR, TOKEN, null, InfobipNapiError(101, "GATEWAY_OPERATION_FAILED", null)),
                "GATEWAY_OPERATION_FAILED"
            ),
            Arguments.of(InfobipSmvVerifyResponse(InfobipSmvStatus.ERROR, TOKEN, null, null), "Silent mobile verification failed"),
            Arguments.of(InfobipSmvVerifyResponse(InfobipSmvStatus.REDIRECT, null, REDIRECT_URL, null), "Silent mobile verification failed"),
        )

        @JvmStatic
        fun callbacks(): Stream<Arguments> = Stream.of(
            Arguments.of(CallbackRequest(TOKEN, VerifyStatus.VALID), VerifyResult(TOKEN, VerifyStatus.VALID)),
            Arguments.of(CallbackRequest(TOKEN, VerifyStatus.INVALID), VerifyResult(TOKEN, VerifyStatus.INVALID)),
            Arguments.of(
                CallbackRequest(TOKEN, VerifyStatus.ERROR, InfobipNapiError(305, "MOBILE_DEVICE_TIMEOUT", "Device timed out")),
                VerifyResult.error(TOKEN, "Device timed out")
            ),
            Arguments.of(
                CallbackRequest(TOKEN, VerifyStatus.ERROR, InfobipNapiError(305, "MOBILE_DEVICE_TIMEOUT", null)),
                VerifyResult.error(TOKEN, "MOBILE_DEVICE_TIMEOUT")
            ),
            Arguments.of(CallbackRequest(TOKEN, null, null), VerifyResult.error(TOKEN, "Silent mobile verification failed")),
        )
    }
}
