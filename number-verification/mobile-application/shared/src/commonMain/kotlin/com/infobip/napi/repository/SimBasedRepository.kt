package com.infobip.napi.repository

import kotlinx.coroutines.CancellationException
import com.infobip.napi.model.domain.SimCredentialError
import com.infobip.napi.model.domain.VerificationOutcome
import com.infobip.napi.model.dto.DcqlResponse
import com.infobip.napi.network.ApiClient
import com.infobip.napi.network.ApiException
import com.infobip.napi.network.SimCredentialController
import com.infobip.napi.network.SimCredentialException
import com.infobip.napi.network.VpToken

/**
 * SIM-based number verification (SBNV) and number share (NS). The backend talks to the verification provider;
 * the app only collects carrier IDs and lets the OS turn the backend's request into a `vp_token`.
 */
interface SimBasedRepository {
    suspend fun verifyPhoneNumber(domain: String, msisdn: String, authorization: String): VerificationOutcome
    suspend fun getPhoneNumber(domain: String, authorization: String): VerificationOutcome
}

class DefaultSimBasedRepository(
    private val apiClient: ApiClient,
    private val simCredentialController: SimCredentialController,
) : SimBasedRepository {

    override suspend fun verifyPhoneNumber(
        domain: String,
        msisdn: String,
        authorization: String,
    ): VerificationOutcome {
        val attempt = Attempt(domain, msisdn, authorization)

        return outcomeOf(attempt) {
            val vpToken = obtainVpToken(attempt) { carrierIds ->
                apiClient.verifyPhoneNumberDcql(domain, carrierIds, authorization)
            } ?: return@outcomeOf attempt.error(CREDENTIAL_ERROR_MESSAGE)

            val response = apiClient.verifyPhoneNumber(domain, vpToken, msisdn, authorization)
            when (response.verified) {
                true -> VerificationOutcome.Valid(msisdn = msisdn, nonce = attempt.nonce)
                false -> VerificationOutcome.Invalid(msisdn = msisdn, nonce = attempt.nonce)
                null -> attempt.error(RESULT_ERROR_MESSAGE)
            }
        }
    }

    override suspend fun getPhoneNumber(domain: String, authorization: String): VerificationOutcome {
        val attempt = Attempt(domain, msisdn = "", authorization)

        return outcomeOf(attempt) {
            val vpToken = obtainVpToken(attempt) { carrierIds ->
                apiClient.getPhoneNumberDcql(domain, carrierIds, authorization)
            } ?: return@outcomeOf attempt.error(CREDENTIAL_ERROR_MESSAGE)

            val phoneNumber = apiClient.getPhoneNumber(domain, vpToken, authorization).phoneNumber
                ?.takeIf { it.isNotBlank() }
                ?: return@outcomeOf attempt.error(RESULT_ERROR_MESSAGE)
            VerificationOutcome.PhoneNumber(msisdn = phoneNumber, nonce = attempt.nonce)
        }
    }

    private suspend fun outcomeOf(attempt: Attempt, block: suspend () -> VerificationOutcome): VerificationOutcome =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            attempt.error(e.errorDescription ?: GENERIC_ERROR_MESSAGE, additionalError = e.message)
        } catch (e: SimCredentialException) {
            attempt.error(
                e.error.description,
                additionalError = e.credentialError ?: e.message,
            )
        } catch (e: Exception) {
            attempt.error(GENERIC_ERROR_MESSAGE, additionalError = e.message)
        }

    /**
     * Carrier IDs, the backend's DCQL call and CredentialManager. Returns null when the credential
     * holds no `vp_token`, and throws when the OS returned an OpenID4VP error instead of one. The nonce
     * is kept on [attempt], so every outcome after the DCQL call carries it. The `vp_token` is passed on
     * as it is; the app does not read it.
     */
    private suspend fun obtainVpToken(
        attempt: Attempt,
        dcql: suspend (List<Int>) -> DcqlResponse,
    ): String? {
        val carrierIds = simCredentialController.activeCarrierIds()
        if (carrierIds.isEmpty()) throw SimCredentialException(SimCredentialError.NO_SIM)

        val dcqlResponse = dcql(carrierIds)
        attempt.nonce = dcqlResponse.nonce
        val credentialRequest = dcqlResponse.credentialRequest ?: return null

        val credentialJson = simCredentialController.requestCredential(credentialRequest)
        VpToken.extract(credentialJson)?.let { return it }
        if (!VpToken.hasError(credentialJson)) return null
        throw SimCredentialException(
            SimCredentialError.ERROR,
            credentialError = "Credential Manager returned an error: $credentialJson",
        )
    }

    private class Attempt(
        val domain: String,
        val msisdn: String,
        val authorization: String,
    ) {
        var nonce: String? = null

        fun error(message: String, additionalError: String? = null) = VerificationOutcome.Error(
            msisdn = msisdn,
            message = message,
            additionalError = additionalError,
            nonce = nonce,
        )
    }

    private companion object {
        const val CREDENTIAL_ERROR_MESSAGE = "Unable to read the credential from the SIM"
        const val RESULT_ERROR_MESSAGE = "Verification returned an error"
        const val GENERIC_ERROR_MESSAGE = "Something went wrong"
    }
}
