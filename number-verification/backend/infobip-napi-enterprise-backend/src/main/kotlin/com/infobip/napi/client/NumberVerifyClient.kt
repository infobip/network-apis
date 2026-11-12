package com.infobip.napi.client

import com.infobip.napi.client.models.InfobipCamaraError
import com.infobip.napi.client.models.InfobipCamaraResult
import com.infobip.napi.client.models.InfobipDcqlCredential
import com.infobip.napi.client.models.InfobipDcqlRequest
import com.infobip.napi.client.models.InfobipDevicePhoneNumberResponse
import com.infobip.napi.client.models.InfobipNumberVerifyResponse
import com.infobip.napi.client.models.InfobipPhoneNumberRequest
import com.infobip.napi.client.models.Ts43UseCase
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

private const val X_CORRELATOR = "X-Correlator"
private const val NV_V0_VERIFY_PATH = "/camara/number-verification/v0/verify"
private const val NV_V2_VERIFY_PATH = "/camara/number-verification/v2/verify"
private const val NV_V2_DEVICE_PHONE_NUMBER_PATH = "/camara/number-verification/v2/device-phone-number"
private const val NV_V2_DCQL_VERIFY_PATH = "/camara/number-verification/v2/dcql/verify"
private const val NV_V2_DCQL_DEVICE_PHONE_NUMBER_PATH = "/camara/number-verification/v2/dcql/device-phone-number"

@Component
class NumberVerifyClient(
    private val infobipRestClient: RestClient
) {
    fun verify(code: String, phoneNumber: String): InfobipCamaraResult<InfobipNumberVerifyResponse> =
        infobipRestClient.post()
            .uri(NV_V0_VERIFY_PATH)
            .header(X_CORRELATOR, code)
            .contentType(MediaType.APPLICATION_JSON)
            .body(InfobipPhoneNumberRequest(toE164(phoneNumber)))
            .exchangeCamara()

    fun verifyTs43(vpToken: String, phoneNumber: String): InfobipCamaraResult<InfobipNumberVerifyResponse> =
        infobipRestClient.post()
            .uri(NV_V2_VERIFY_PATH)
            .header(X_CORRELATOR, vpToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(InfobipPhoneNumberRequest(toE164(phoneNumber)))
            .exchangeCamara()

    fun getPhoneNumber(vpToken: String): InfobipCamaraResult<InfobipDevicePhoneNumberResponse> =
        infobipRestClient.get()
            .uri(NV_V2_DEVICE_PHONE_NUMBER_PATH)
            .header(X_CORRELATOR, vpToken)
            .exchangeCamara()

    fun getDcql(useCase: Ts43UseCase, nonce: String, carrierIds: List<Int>): InfobipCamaraResult<Map<String, InfobipDcqlCredential>> =
        infobipRestClient.post()
            .uri(dcqlPath(useCase))
            .contentType(MediaType.APPLICATION_JSON)
            .body(InfobipDcqlRequest(nonce, carrierIds))
            .exchangeCamara()

    private fun dcqlPath(useCase: Ts43UseCase) = when (useCase) {
        Ts43UseCase.VERIFY_PHONE_NUMBER -> NV_V2_DCQL_VERIFY_PATH
        Ts43UseCase.GET_PHONE_NUMBER -> NV_V2_DCQL_DEVICE_PHONE_NUMBER_PATH
    }

    private fun toE164(phoneNumber: String) = if (phoneNumber.startsWith("+")) phoneNumber else "+$phoneNumber"

    private inline fun <reified T : Any> RestClient.RequestHeadersSpec<*>.exchangeCamara(): InfobipCamaraResult<T> =
        exchangeCamara(typeReference<T>())

    private fun <T : Any> RestClient.RequestHeadersSpec<*>.exchangeCamara(bodyType: ParameterizedTypeReference<T>): InfobipCamaraResult<T> =
        exchangeForRequiredValue { _, response ->
            val correlator = response.headers.getFirst(X_CORRELATOR)
            if (response.statusCode.is2xxSuccessful) {
                InfobipCamaraResult(correlator, response.bodyOrNull(bodyType) ?: throw InfobipCallException(response.statusCode), null)
            } else {
                val error = response.bodyOrNull(typeReference<InfobipCamaraError>())
                    ?.takeIf { it.code != null || it.message != null }
                    ?: throw InfobipCallException(response.statusCode)
                InfobipCamaraResult(correlator, null, error)
            }
        }
}
