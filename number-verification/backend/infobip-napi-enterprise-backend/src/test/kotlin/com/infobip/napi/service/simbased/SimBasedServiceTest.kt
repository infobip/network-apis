package com.infobip.napi.service.simbased

import com.infobip.napi.client.NumberVerifyClient
import com.infobip.napi.client.models.InfobipCamaraError
import com.infobip.napi.client.models.InfobipCamaraResult
import com.infobip.napi.client.models.InfobipDcqlClaim
import com.infobip.napi.client.models.InfobipDcqlCredential
import com.infobip.napi.client.models.InfobipDcqlMeta
import com.infobip.napi.client.models.InfobipDevicePhoneNumberResponse
import com.infobip.napi.client.models.InfobipNumberVerifyResponse
import com.infobip.napi.client.models.Ts43UseCase
import com.infobip.napi.service.RequestRejectedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import tools.jackson.databind.json.JsonMapper

private const val MSISDN = "385911234567"
private const val VP_TOKEN = "vp-token"
private const val MI_REQUEST_ID = "mi-request-1"

@ExtendWith(MockitoExtension::class)
class SimBasedServiceTest {

    @Mock
    private lateinit var numberVerifyClient: NumberVerifyClient

    private val jsonMapper = JsonMapper.builder().build()

    private val service by lazy { SimBasedService(numberVerifyClient, jsonMapper) }

    private fun credential(jwt: String?, id: String = "cred") = InfobipDcqlCredential(
        id = id,
        format = "dc+sd-jwt",
        meta = InfobipDcqlMeta(jwt, listOf("number-verification/device-phone-number/ts43")),
        claims = listOf(InfobipDcqlClaim(listOf("subscription_hint"), listOf(1, 2)))
    )

    private val notFound = InfobipDcqlCredential(status = 404, code = "NOT_FOUND", message = "Not found")

    private fun dcqlResult(credentials: Map<String, InfobipDcqlCredential>?, error: InfobipCamaraError? = null) =
        InfobipCamaraResult(MI_REQUEST_ID, credentials, error)

    private fun givenDcql(useCase: Ts43UseCase, carriers: List<Int>, result: InfobipCamaraResult<Map<String, InfobipDcqlCredential>>) {
        `when`(numberVerifyClient.getDcql(eqNotNull(useCase), anyString(), eqNotNull(carriers))).thenReturn(result)
    }

    private fun <T : Any> eqNotNull(value: T): T = eq(value) ?: value

    private fun verifyPhoneNumberDcql(carriers: List<Int>) = service.verifyPhoneNumberDcql(carriers)

    @Test
    fun verifyPhoneNumberDcql_should_UseVerifyUseCaseAndSendGeneratedNonce_when_Called() {
        givenDcql(Ts43UseCase.VERIFY_PHONE_NUMBER, listOf(1), dcqlResult(mapOf("1" to credential("jwt"))))

        val response = verifyPhoneNumberDcql(listOf(1))

        verify(numberVerifyClient).getDcql(Ts43UseCase.VERIFY_PHONE_NUMBER, response.nonce, listOf(1))
    }

    @Test
    fun getPhoneNumberDcql_should_UseGetPhoneNumberUseCase_when_Called() {
        givenDcql(Ts43UseCase.GET_PHONE_NUMBER, listOf(1), dcqlResult(mapOf("1" to credential("jwt"))))

        val response = service.getPhoneNumberDcql(listOf(1))

        verify(numberVerifyClient).getDcql(Ts43UseCase.GET_PHONE_NUMBER, response.nonce, listOf(1))
    }

    @Test
    fun dcql_should_PickFirstSupportedInAppOrder_when_MapOrderDiffers() {
        givenDcql(
            Ts43UseCase.VERIFY_PHONE_NUMBER, listOf(1, 2, 3),
            dcqlResult(linkedMapOf("3" to credential("jwt-3"), "2" to notFound, "1" to credential("jwt-1")))
        )

        val response = verifyPhoneNumberDcql(listOf(1, 2, 3))

        assertThat(response.carrierId).isEqualTo(1)
        assertThat(response.credentialRequest).contains("jwt-1").doesNotContain("jwt-3")
    }

    @Test
    fun dcql_should_SkipErrorEntriesAndMissingCarriers_when_FirstCarriersUnsupported() {
        givenDcql(
            Ts43UseCase.VERIFY_PHONE_NUMBER, listOf(1, 2, 3),
            dcqlResult(mapOf("2" to notFound, "3" to credential("jwt-3")))
        )

        assertThat(verifyPhoneNumberDcql(listOf(1, 2, 3)).carrierId).isEqualTo(3)
    }

    @Test
    fun dcql_should_Reject_when_NoCarrierSupported() {
        givenDcql(Ts43UseCase.VERIFY_PHONE_NUMBER, listOf(1234, 5678), dcqlResult(mapOf("1234" to notFound)))

        assertThatThrownBy { verifyPhoneNumberDcql(listOf(1234, 5678)) }
            .isInstanceOf(RequestRejectedException::class.java)
            .hasMessage("No supported carrier: 1234 NOT_FOUND, 5678 no response")
    }

    @Test
    fun dcql_should_Reject_when_CarrierHasNoCredentialAuthorizationJwtAndNoError() {
        givenDcql(Ts43UseCase.VERIFY_PHONE_NUMBER, listOf(1), dcqlResult(mapOf("1" to credential(null))))

        assertThatThrownBy { verifyPhoneNumberDcql(listOf(1)) }
            .isInstanceOf(RequestRejectedException::class.java)
            .hasMessage("No supported carrier: 1 NOT_SUPPORTED")
    }

    @Test
    fun dcql_should_Reject_when_TopLevelError() {
        givenDcql(Ts43UseCase.VERIFY_PHONE_NUMBER, listOf(1), dcqlResult(null, InfobipCamaraError(400, "BAD", "Bad things")))

        assertThatThrownBy { verifyPhoneNumberDcql(listOf(1)) }
            .isInstanceOf(RequestRejectedException::class.java)
            .hasMessage("Bad things")
    }

    @Test
    fun dcql_should_BuildOpenId4VpRequestWithSnakeCaseKeysAndSameNonce_when_CarrierSupported() {
        givenDcql(Ts43UseCase.VERIFY_PHONE_NUMBER, listOf(1), dcqlResult(mapOf("1" to credential("jwt"))))

        val response = verifyPhoneNumberDcql(listOf(1))

        val request = jsonMapper.readTree(response.credentialRequest).path("requests").path(0)
        assertThat(request.path("protocol").asString()).isEqualTo("openid4vp-v1-unsigned")
        val data = request.path("data")
        assertThat(data.path("response_type").asString()).isEqualTo("vp_token")
        assertThat(data.path("response_mode").asString()).isEqualTo("dc_api")
        assertThat(data.path("nonce").asString()).isEqualTo(response.nonce)
        val cred = data.path("dcql_query").path("credentials").path(0)
        assertThat(cred.path("id").asString()).isEqualTo("cred")
        assertThat(cred.path("format").asString()).isEqualTo("dc+sd-jwt")
        assertThat(cred.path("meta").path("credential_authorization_jwt").asString()).isEqualTo("jwt")
        assertThat(cred.path("meta").path("vct_values").path(0).asString())
            .isEqualTo("number-verification/device-phone-number/ts43")
        assertThat(cred.path("claims").path(0).path("path").path(0).asString()).isEqualTo("subscription_hint")
        assertThat(cred.path("claims").path(0).path("values").path(1).asInt()).isEqualTo(2)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun verifyPhoneNumber_should_ReturnVerified_when_InfobipAnswersTrueOrFalse(verified: Boolean) {
        `when`(numberVerifyClient.verifyTs43(VP_TOKEN, MSISDN))
            .thenReturn(InfobipCamaraResult(MI_REQUEST_ID, InfobipNumberVerifyResponse(verified), null))

        assertThat(service.verifyPhoneNumber(VP_TOKEN, MSISDN).verified).isEqualTo(verified)
    }

    @Test
    fun verifyPhoneNumber_should_Reject_when_InfobipReturnsError() {
        `when`(numberVerifyClient.verifyTs43(VP_TOKEN, MSISDN))
            .thenReturn(InfobipCamaraResult(MI_REQUEST_ID, null, InfobipCamaraError(403, "NAME", null)))

        assertThatThrownBy { service.verifyPhoneNumber(VP_TOKEN, MSISDN) }
            .isInstanceOf(RequestRejectedException::class.java)
            .hasMessage("NAME")
    }

    @Test
    fun getPhoneNumber_should_ReturnNumber_when_InfobipAnswersWithNumber() {
        `when`(numberVerifyClient.getPhoneNumber(VP_TOKEN))
            .thenReturn(InfobipCamaraResult(MI_REQUEST_ID, InfobipDevicePhoneNumberResponse(MSISDN), null))

        assertThat(service.getPhoneNumber(VP_TOKEN).phoneNumber).isEqualTo(MSISDN)
    }

    @Test
    fun getPhoneNumber_should_Reject_when_InfobipReturnsError() {
        `when`(numberVerifyClient.getPhoneNumber(VP_TOKEN))
            .thenReturn(InfobipCamaraResult(MI_REQUEST_ID, null, InfobipCamaraError(404, "N", "Desc")))

        assertThatThrownBy { service.getPhoneNumber(VP_TOKEN) }
            .isInstanceOf(RequestRejectedException::class.java)
            .hasMessage("Desc")
    }

    @Test
    fun dcqlResponseToString_should_ShortenCredentialAuthorizationJwt_when_Logged() {
        val jwt = "AAAAAAAAAA" + "x".repeat(30) + "BBBBBBBBBB"
        givenDcql(Ts43UseCase.VERIFY_PHONE_NUMBER, listOf(1), dcqlResult(mapOf("1" to credential(jwt))))

        val response = verifyPhoneNumberDcql(listOf(1))

        assertThat(response.credentialRequest).contains(jwt)
        assertThat(response.toString())
            .contains("\"credential_authorization_jwt\":\"AAAAAAAAAA...BBBBBBBBBB\"")
            .contains("nonce=${response.nonce}", "carrierId=1")
            .doesNotContain(jwt)
    }
}
