package com.infobip.napi.service.simbased

import com.infobip.napi.api.models.numberverify.simbased.DcqlResponse
import com.infobip.napi.api.models.numberverify.simbased.GetPhoneNumberResponse
import com.infobip.napi.api.models.numberverify.simbased.VerifyPhoneNumberResponse
import com.infobip.napi.client.NumberVerifyClient
import com.infobip.napi.client.models.InfobipCamaraError
import com.infobip.napi.client.models.InfobipDcqlCredential
import com.infobip.napi.client.models.Ts43UseCase
import com.infobip.napi.service.RequestRejectedException
import com.infobip.napi.utils.LogUtils.abbreviateVpToken
import com.infobip.napi.utils.LogUtils.credentialsForLog
import com.infobip.napi.utils.LogUtils.logCorrelationPrefix
import com.infobip.napi.utils.LogUtils.logPrefixWithEnterpriseBeRequestId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

private val log = LoggerFactory.getLogger(SimBasedService::class.java)

private const val DCQL_ERROR_DESCRIPTION = "Credential request failed"
private const val VERIFY_PHONE_NUMBER_ERROR_DESCRIPTION = "Verify phone number failed"
private const val GET_PHONE_NUMBER_ERROR_DESCRIPTION = "Get phone number failed"
private const val NO_RESPONSE = "no response"

@Service
class SimBasedService(
    private val numberVerifyClient: NumberVerifyClient,
    private val jsonMapper: JsonMapper
) {

    fun verifyPhoneNumberDcql(carrierIds: List<Int>): DcqlResponse = dcql(Ts43UseCase.VERIFY_PHONE_NUMBER, carrierIds)

    fun getPhoneNumberDcql(carrierIds: List<Int>): DcqlResponse = dcql(Ts43UseCase.GET_PHONE_NUMBER, carrierIds)

    fun verifyPhoneNumber(vpToken: String, phoneNumber: String): VerifyPhoneNumberResponse {
        val claims = parseVpTokenClaims(vpToken, jsonMapper)
        log.debug(
            "${logCorrelationPrefix(claims?.nonce, claims?.state)} Verify phone number request to Infobip: " +
                "sdJwt={}, msisdn={}",
            abbreviateVpToken(vpToken), phoneNumber
        )
        val response = numberVerifyClient.verifyTs43(vpToken, phoneNumber)
        val verified = response.body?.devicePhoneNumberVerified ?: throw rejected(
            logCorrelationPrefix(claims?.nonce, claims?.state), "Verify phone number",
            response.error, VERIFY_PHONE_NUMBER_ERROR_DESCRIPTION
        )
        log.debug(
            "${logCorrelationPrefix(claims?.nonce, claims?.state)} Verify phone number finished with verified: {}",
            verified
        )
        return VerifyPhoneNumberResponse(verified)
    }

    fun getPhoneNumber(vpToken: String): GetPhoneNumberResponse {
        val claims = parseVpTokenClaims(vpToken, jsonMapper)
        log.debug(
            "${logCorrelationPrefix(claims?.nonce, claims?.state)} Get phone number request to Infobip: sdJwt={}",
            abbreviateVpToken(vpToken)
        )
        val response = numberVerifyClient.getPhoneNumber(vpToken)
        val phoneNumber = response.body?.devicePhoneNumber ?: throw rejected(
            logCorrelationPrefix(claims?.nonce, claims?.state), "Get phone number",
            response.error, GET_PHONE_NUMBER_ERROR_DESCRIPTION
        )
        log.debug("${logCorrelationPrefix(claims?.nonce, claims?.state)} Get phone number finished with a phone number")
        return GetPhoneNumberResponse(phoneNumber)
    }

    private fun dcql(useCase: Ts43UseCase, carrierIds: List<Int>): DcqlResponse {
        val nonce = UUID.randomUUID().toString()
        log.debug(
            "${logPrefixWithEnterpriseBeRequestId(nonce)} DCQL requested for use case: {}, carriers: {}",
            useCase, carrierIds
        )

        val response = numberVerifyClient.getDcql(useCase, nonce, carrierIds)
        log.debug(
            "${logCorrelationPrefix(nonce, response.correlator)} DCQL responded with error: {}, credentials: {}",
            response.error?.code, credentialsForLog(response.body)
        )
        val credentials = response.body
        if (response.error != null || credentials == null) {
            throw rejected(logCorrelationPrefix(nonce, response.correlator), "DCQL", response.error, DCQL_ERROR_DESCRIPTION)
        }

        val (carrierId, credential, credentialAuthorizationJwt) = carrierIds.firstNotNullOfOrNull { id ->
            credentials[id.toString()]?.let { credential ->
                credential.meta?.credentialAuthorizationJwt?.let { Triple(id, credential, it) }
            }
        } ?: run {
            val carrierErrors = carrierIds.joinToString(", ") { id ->
                "$id ${credentials[id.toString()]?.let { it.code ?: "NOT_SUPPORTED" } ?: NO_RESPONSE}"
            }
            log.warn("${logCorrelationPrefix(nonce, response.correlator)} No supported carrier: {}", carrierErrors)
            throw RequestRejectedException("No supported carrier: $carrierErrors")
        }
        val dcqlResponse = DcqlResponse(
            nonce,
            carrierId,
            jsonMapper.writeValueAsString(toOpenId4VpRequest(nonce, credential, credentialAuthorizationJwt))
        )
        log.debug(
            "${logCorrelationPrefix(nonce, response.correlator)} Chosen DCQL response: {}",
            dcqlResponse
        )

        return dcqlResponse
    }

    private fun toOpenId4VpRequest(
        nonce: String,
        credential: InfobipDcqlCredential,
        credentialAuthorizationJwt: String
    ) = OpenId4VpRequest(
        listOf(
            OpenId4VpProtocolRequest(
                "openid4vp-v1-unsigned",
                OpenId4VpData(
                    "vp_token",
                    "dc_api",
                    nonce,
                    DcqlQuery(
                        listOf(
                            DcqlCredential(
                                credential.id,
                                credential.format,
                                DcqlCredentialMeta(credentialAuthorizationJwt, credential.meta?.vctValues),
                                credential.claims?.map { DcqlCredentialClaim(it.path, it.values) }
                            )
                        )
                    )
                )
            )
        )
    )

    private fun rejected(logPrefix: String, operation: String, error: InfobipCamaraError?, default: String): RequestRejectedException {
        log.warn("$logPrefix $operation rejected with error: {} {} {}", error?.status, error?.code, error?.message)
        return RequestRejectedException(error?.message ?: error?.code ?: default)
    }
}
