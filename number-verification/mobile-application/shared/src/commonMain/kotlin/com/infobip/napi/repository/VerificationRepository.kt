package com.infobip.napi.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.infobip.napi.config.AppConfig
import com.infobip.napi.model.domain.CellularResult
import com.infobip.napi.model.domain.VerificationOutcome
import com.infobip.napi.model.domain.VerificationType
import com.infobip.napi.model.dto.ClientVerifyResultResponse
import com.infobip.napi.model.dto.IpAddress
import com.infobip.napi.network.ApiClient
import com.infobip.napi.network.ApiException
import com.infobip.napi.network.CellularController
import com.infobip.napi.network.RedirectFollower

interface VerificationRepository {
    suspend fun verify(
        domain: String,
        msisdn: String,
        authorization: String,
        type: VerificationType,
        debugEnabled: Boolean,
    ): VerificationOutcome
}

class DefaultVerificationRepository(
    private val apiClient: ApiClient,
    private val cellularController: CellularController,
    private val redirectFollower: RedirectFollower,
) : VerificationRepository {

    override suspend fun verify(
        domain: String,
        msisdn: String,
        authorization: String,
        type: VerificationType,
        debugEnabled: Boolean,
    ): VerificationOutcome {
        val attempt = Attempt(domain, msisdn, authorization)

        return try {
            var networkReset = false
            val cellularFailure = try {
                verifyOverCellular(attempt, type, debugEnabled)
            } finally {
                networkReset = withContext(NonCancellable) { cellularController.resetToDefault() }
            }
            if (cellularFailure != null) return cellularFailure
            if (!networkReset) return attempt.error(CellularResult.FAIL_NETWORK_RESET.description)

            val verifyToken = attempt.token ?: return attempt.error(FAILED_RESULT_MESSAGE)
            val result = pollWithRetries(attempt, verifyToken) ?: return attempt.error(FAILED_RESULT_MESSAGE)
            val resultToken = result.token ?: verifyToken
            attempt.token = resultToken
            mapResult(attempt, result, resultToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            attempt.error(e.errorDescription ?: GENERIC_ERROR_MESSAGE, additionalError = e.message)
        } catch (e: Exception) {
            attempt.error(GENERIC_ERROR_MESSAGE, additionalError = e.message)
        }
    }

    private suspend fun verifyOverCellular(
        attempt: Attempt,
        type: VerificationType,
        debugEnabled: Boolean,
    ): VerificationOutcome.Error? {
        val switchResult = cellularController.switchToCellular()
        if (switchResult != CellularResult.SUCCESS && switchResult != CellularResult.MANUAL_REQUIRED) {
            return attempt.error(switchResult.description)
        }

        // Fetched over the same network as the verify request, so it shows the address the backend
        // will actually see. It is diagnostic only and must not abort the verification.
        if (debugEnabled) attempt.deviceAddress = fetchDeviceAddressOrNull(attempt)

        val verifyResponse = apiClient.sendVerifyRequest(
            domain = attempt.domain,
            msisdn = attempt.msisdn,
            authorization = attempt.authorization,
            type = type,
        )
        attempt.token = verifyResponse.token
        verifyResponse.errorDescription?.let { return attempt.error(it) }

        val redirectUrl = verifyResponse.deviceRedirectUrl ?: return null
        val redirect = redirectFollower.follow(redirectUrl, attempt.domain)
        if (redirect.isSuccess) return null
        return attempt.error(
            message = redirect.message,
            additionalError = listOfNotNull(
                if (redirect.headers != null) "Headers:\n" + redirect.headers else null,
                if (redirect.body != null) "Body:\n" + redirect.body else null
            ).filter { it.isNotBlank() }
                .joinToString("\n\n")
                .ifEmpty { null },
        )
    }

    private suspend fun fetchDeviceAddressOrNull(attempt: Attempt): IpAddress? =
        try {
            apiClient.fetchDeviceAddress(attempt.domain, attempt.authorization)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    /**
     * The backend keeps a completed result until it expires and returns it on every read, so a poll
     * that fails in transit or with a server error can simply be retried. A client error means the
     * request itself is wrong and is not retried.
     */
    private suspend fun pollWithRetries(attempt: Attempt, token: String): ClientVerifyResultResponse? {
        var lastFailure: Exception? = null
        repeat(AppConfig.POLLING_RETRY_LIMIT) { index ->
            if (index > 0) delay(AppConfig.pollingRetryInterval)
            try {
                apiClient.pollForResult(attempt.domain, token, attempt.authorization)?.let { return it }
                lastFailure = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (!e.isServerError) throw e
                lastFailure = e
            } catch (e: Exception) {
                lastFailure = e
            }
        }
        lastFailure?.let { throw it }
        return null
    }

    private fun mapResult(
        attempt: Attempt,
        response: ClientVerifyResultResponse,
        token: String,
    ): VerificationOutcome = when (response.result?.uppercase()) {
        RESULT_VALID -> VerificationOutcome.Valid(
            msisdn = attempt.msisdn,
            token = token,
            deviceIp = attempt.deviceAddress?.address,
            devicePort = attempt.deviceAddress?.port,
        )

        RESULT_INVALID -> VerificationOutcome.Invalid(
            msisdn = attempt.msisdn,
            token = token,
            deviceIp = attempt.deviceAddress?.address,
            devicePort = attempt.deviceAddress?.port,
        )

        else -> attempt.error(response.errorDescription ?: RESULT_ERROR_MESSAGE)
    }

    private class Attempt(
        val domain: String,
        val msisdn: String,
        val authorization: String,
    ) {
        var token: String? = null
        var deviceAddress: IpAddress? = null

        fun error(message: String, additionalError: String? = null) = VerificationOutcome.Error(
            msisdn = msisdn,
            message = message,
            token = token,
            deviceIp = deviceAddress?.address,
            devicePort = deviceAddress?.port,
            additionalError = additionalError,
        )
    }

    private companion object {
        const val RESULT_VALID = "VALID"
        const val RESULT_INVALID = "INVALID"
        const val FAILED_RESULT_MESSAGE = "Failed to fetch verification result"
        const val RESULT_ERROR_MESSAGE = "Verification returned an error"
        const val GENERIC_ERROR_MESSAGE = "Something went wrong"
    }
}
