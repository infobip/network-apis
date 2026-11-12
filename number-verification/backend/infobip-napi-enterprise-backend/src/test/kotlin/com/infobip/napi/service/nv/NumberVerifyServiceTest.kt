package com.infobip.napi.service.nv

import com.infobip.napi.api.models.numberverify.VerifyStatus
import com.infobip.napi.client.NumberVerifyClient
import com.infobip.napi.client.models.InfobipCamaraError
import com.infobip.napi.client.models.InfobipCamaraResult
import com.infobip.napi.client.models.InfobipNumberVerifyResponse
import com.infobip.napi.service.VerifyUrlBuilder
import com.infobip.napi.service.store.AuthorizationStateStore
import com.infobip.napi.service.store.VerificationRequestDetails
import com.infobip.napi.service.store.VerificationResultStore
import com.infobip.napi.service.store.VerifyResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID
import java.util.stream.Stream

private const val MSISDN = "385911234567"
private const val CLIENT_ID = "client-1"
private const val STATE = "state-1"
private const val NV_TOKEN = "nv-token"
private const val AUTH_CODE = "auth-code"
private const val AUTHORIZE_URL = "https://api.example.com/authorize"

@ExtendWith(MockitoExtension::class)
class NumberVerifyServiceTest {

    @Mock
    private lateinit var resultStore: VerificationResultStore

    @Mock
    private lateinit var stateStore: AuthorizationStateStore

    @Mock
    private lateinit var urlBuilder: VerifyUrlBuilder

    @Mock
    private lateinit var numberVerifyClient: NumberVerifyClient

    @InjectMocks
    private lateinit var service: NumberVerifyService

    private val verificationRequestDetails = VerificationRequestDetails(MSISDN)

    @Test
    fun startVerification_should_StoreStateRegisterPendingAndReturnAuthorizeUrl_when_Called() {
        `when`(urlBuilder.camaraAuthorizeUrl(anyString(), anyString(), anyString())).thenReturn(AUTHORIZE_URL)

        val response = service.startVerification(MSISDN, CLIENT_ID)

        val state = response.token
        assertThat(UUID.fromString(state).toString()).isEqualTo(state)
        assertThat(response.deviceRedirectUrl).isEqualTo(AUTHORIZE_URL)
        val inOrder = inOrder(stateStore, resultStore, urlBuilder)
        inOrder.verify(stateStore).create(state, verificationRequestDetails)
        inOrder.verify(resultStore).registerPending(state)
        inOrder.verify(urlBuilder).camaraAuthorizeUrl(state, CLIENT_ID, MSISDN)
        verifyNoInteractions(numberVerifyClient)
    }

    @ParameterizedTest
    @MethodSource("nvResponses")
    fun completeAuthorization_should_StoreMappedResult_when_InfobipResponds(
        response: InfobipCamaraResult<InfobipNumberVerifyResponse>,
        expected: VerifyResult
    ) {
        `when`(stateStore.consume(STATE)).thenReturn(verificationRequestDetails)
        `when`(numberVerifyClient.verify(AUTH_CODE, MSISDN)).thenReturn(response)

        service.completeAuthorization(STATE, AUTH_CODE)

        val inOrder = inOrder(stateStore, numberVerifyClient, resultStore)
        inOrder.verify(stateStore).consume(STATE)
        inOrder.verify(numberVerifyClient).verify(AUTH_CODE, MSISDN)
        inOrder.verify(resultStore).complete(expected)
    }

    @Test
    fun completeAuthorization_should_StoreErrorResult_when_InfobipCallFails() {
        `when`(stateStore.consume(STATE)).thenReturn(verificationRequestDetails)
        `when`(numberVerifyClient.verify(AUTH_CODE, MSISDN)).thenThrow(IllegalStateException("Infobip unavailable"))

        service.completeAuthorization(STATE, AUTH_CODE)

        verify(resultStore).complete(VerifyResult.error(STATE, "Number verification failed"))
    }

    @Test
    fun completeAuthorization_should_NotCallInfobip_when_StateUnknownOrAlreadyUsed() {
        `when`(stateStore.consume(STATE)).thenReturn(null)

        service.completeAuthorization(STATE, AUTH_CODE)

        verifyNoInteractions(numberVerifyClient, resultStore)
    }

    @ParameterizedTest
    @MethodSource("authorizationFailures")
    fun failAuthorization_should_ConsumeStateAndStoreErrorResult_when_StateExists(
        error: String?,
        errorDescription: String?,
        expectedDescription: String
    ) {
        `when`(stateStore.consume(STATE)).thenReturn(verificationRequestDetails)

        service.failAuthorization(STATE, error, errorDescription)

        verify(resultStore).complete(VerifyResult.error(STATE, expectedDescription))
        verifyNoInteractions(numberVerifyClient)
    }

    @Test
    fun failAuthorization_should_DoNothing_when_StateUnknown() {
        `when`(stateStore.consume(STATE)).thenReturn(null)

        service.failAuthorization(STATE, "access_denied", null)

        verifyNoInteractions(numberVerifyClient, resultStore)
    }

    companion object {
        @JvmStatic
        fun nvResponses(): Stream<Arguments> = Stream.of(
            Arguments.of(
                InfobipCamaraResult(NV_TOKEN, InfobipNumberVerifyResponse(true), null),
                VerifyResult(STATE, VerifyStatus.VALID)
            ),
            Arguments.of(
                InfobipCamaraResult(NV_TOKEN, InfobipNumberVerifyResponse(false), null),
                VerifyResult(STATE, VerifyStatus.INVALID)
            ),
            Arguments.of(
                InfobipCamaraResult<InfobipNumberVerifyResponse>(
                    NV_TOKEN, null, InfobipCamaraError(403, "PERMISSION_DENIED", "Token expired")
                ),
                VerifyResult.error(STATE, "Token expired")
            ),
            Arguments.of(
                InfobipCamaraResult<InfobipNumberVerifyResponse>(NV_TOKEN, null, InfobipCamaraError(403, "PERMISSION_DENIED", null)),
                VerifyResult.error(STATE, "PERMISSION_DENIED")
            ),
            Arguments.of(
                InfobipCamaraResult(NV_TOKEN, InfobipNumberVerifyResponse(null), null),
                VerifyResult.error(STATE, "Number verification failed")
            ),
        )

        @JvmStatic
        fun authorizationFailures(): Stream<Arguments> = Stream.of(
            Arguments.of("access_denied", "User denied access", "User denied access"),
            Arguments.of("access_denied", null, "access_denied"),
            Arguments.of(null, null, "Number verification failed"),
        )
    }
}
