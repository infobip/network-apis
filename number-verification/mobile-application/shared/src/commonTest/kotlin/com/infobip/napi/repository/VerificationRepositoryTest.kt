package com.infobip.napi.repository

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import com.infobip.napi.config.AppConfig
import com.infobip.napi.model.domain.CellularResult
import com.infobip.napi.model.domain.VerificationOutcome
import com.infobip.napi.model.domain.VerificationType
import com.infobip.napi.network.ApiClient
import com.infobip.napi.network.RedirectFollower
import com.infobip.napi.network.mockHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerificationRepositoryTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val domain = "https://example.com"

    private fun repository(
        cellular: FakeCellularController,
        handler: io.ktor.client.engine.mock.MockRequestHandler,
    ): VerificationRepository {
        val client = mockHttpClient(handler)
        return DefaultVerificationRepository(
            apiClient = ApiClient(client),
            cellularController = cellular,
            redirectFollower = RedirectFollower(client),
        )
    }

    private suspend fun VerificationRepository.run(camara: Boolean = false, debug: Boolean = false) =
        verify(
            domain = domain,
            msisdn = "38591234567",
            authorization = "Bearer key-1",
            type = if (camara) VerificationType.CAMARA else VerificationType.SMV,
            debugEnabled = debug,
        )

    @Test
    fun verify_should_ReturnValid_when_ResultIsValid() = runTest {
        val cellular = FakeCellularController()
        val repo = repository(cellular) { request ->
            when {
                request.url.encodedPath.startsWith("/napi/verify/result/") ->
                    respond("""{"result":"VALID"}""", HttpStatusCode.OK, jsonHeaders)

                else -> respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        val outcome = repo.run()

        val valid = assertIs<VerificationOutcome.Valid>(outcome)
        assertEquals("tok-1", valid.token)
        assertEquals(1, cellular.switchCount)
        assertEquals(1, cellular.resetCount)
    }

    @Test
    fun verify_should_ReturnInvalid_when_ResultIsInvalid() = runTest {
        val repo = repository(FakeCellularController()) { request ->
            when {
                request.url.encodedPath.startsWith("/napi/verify/result/") ->
                    respond("""{"result":"INVALID"}""", HttpStatusCode.OK, jsonHeaders)

                else -> respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        assertIs<VerificationOutcome.Invalid>(repo.run())
    }

    @Test
    fun verify_should_ReturnError_when_ResultIsUnknownStatus() = runTest {
        val repo = repository(FakeCellularController()) { request ->
            when {
                request.url.encodedPath.startsWith("/napi/verify/result/") ->
                    respond("""{"result":"WEIRD"}""", HttpStatusCode.OK, jsonHeaders)

                else -> respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        val error = assertIs<VerificationOutcome.Error>(repo.run())
        assertEquals("Verification returned an error", error.message)
    }

    @Test
    fun verify_should_PollUntilResultReady_when_FirstAttemptsAre204() = runTest {
        var pollCount = 0
        val repo = repository(FakeCellularController()) { request ->
            when {
                request.url.encodedPath.startsWith("/napi/verify/result/") -> {
                    pollCount++
                    if (pollCount < AppConfig.POLLING_RETRY_LIMIT) {
                        respond("", HttpStatusCode.NoContent)
                    } else {
                        respond("""{"result":"VALID"}""", HttpStatusCode.OK, jsonHeaders)
                    }
                }

                else -> respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        val outcome = repo.run()

        assertIs<VerificationOutcome.Valid>(outcome)
        assertEquals(AppConfig.POLLING_RETRY_LIMIT, pollCount)
    }

    @Test
    fun verify_should_ReturnError_when_PollingExhaustedWithoutResult() = runTest {
        var pollCount = 0
        val repo = repository(FakeCellularController()) { request ->
            when {
                request.url.encodedPath.startsWith("/napi/verify/result/") -> {
                    pollCount++
                    respond("", HttpStatusCode.NoContent)
                }

                else -> respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        val error = assertIs<VerificationOutcome.Error>(repo.run())
        assertEquals("Failed to fetch verification result", error.message)
        assertEquals(AppConfig.POLLING_RETRY_LIMIT, pollCount)
    }

    @Test
    fun verify_should_ReturnError_when_VerifyResponseHasErrorDescription() = runTest {
        val cellular = FakeCellularController()
        val repo = repository(cellular) {
            respond("""{"errorDescription":"invalid api key"}""", HttpStatusCode.OK, jsonHeaders)
        }

        val error = assertIs<VerificationOutcome.Error>(repo.run())
        assertEquals("invalid api key", error.message)
        assertEquals(1, cellular.resetCount)
    }

    @Test
    fun verify_should_ReturnError_when_CellularSwitchFails() = runTest {
        val cellular = FakeCellularController(switchResult = CellularResult.CELLULAR_UNAVAILABLE)
        var verifyCalled = false
        val repo = repository(cellular) {
            verifyCalled = true
            respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
        }

        val error = assertIs<VerificationOutcome.Error>(repo.run())
        assertEquals(CellularResult.CELLULAR_UNAVAILABLE.description, error.message)
        assertTrue(!verifyCalled)
        assertEquals(1, cellular.resetCount)
    }

    @Test
    fun verify_should_ResetNetworkAndRethrow_when_Cancelled() = runTest {
        val cellular = FakeCellularController()
        val repo = repository(cellular) {
            throw CancellationException("cancelled")
        }

        assertFailsWith<CancellationException> { repo.run() }
        assertEquals(1, cellular.resetCount)
    }

    @Test
    fun verify_should_ResetNetworkBeforePolling_when_VerifySucceeds() = runTest {
        val cellular = FakeCellularController()
        var resetCountAtPoll = -1
        val repo = repository(cellular) { request ->
            when {
                request.url.encodedPath.startsWith("/napi/verify/result/") -> {
                    resetCountAtPoll = cellular.resetCount
                    respond("""{"result":"VALID"}""", HttpStatusCode.OK, jsonHeaders)
                }

                else -> respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        repo.run()

        assertEquals(1, resetCountAtPoll)
    }

    @Test
    fun verify_should_ProceedWithVerification_when_CellularSwitchRequiresManual() = runTest {
        val repo = repository(FakeCellularController(switchResult = CellularResult.MANUAL_REQUIRED)) { request ->
            when {
                request.url.encodedPath.startsWith("/napi/verify/result/") ->
                    respond("""{"result":"VALID"}""", HttpStatusCode.OK, jsonHeaders)

                else -> respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        assertIs<VerificationOutcome.Valid>(repo.run())
    }

    @Test
    fun verify_should_ReturnError_when_NetworkResetFails() = runTest {
        val repo = repository(FakeCellularController(resetResult = false)) {
            respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
        }

        val error = assertIs<VerificationOutcome.Error>(repo.run())
        assertEquals(CellularResult.FAIL_NETWORK_RESET.description, error.message)
    }

    @Test
    fun verify_should_ReturnError_when_TokenMissing() = runTest {
        val repo = repository(FakeCellularController()) {
            respond("""{}""", HttpStatusCode.OK, jsonHeaders)
        }

        val error = assertIs<VerificationOutcome.Error>(repo.run())
        assertEquals("Failed to fetch verification result", error.message)
    }

    @Test
    fun verify_should_ResetAndReturnError_when_ExceptionThrown() = runTest {
        val cellular = FakeCellularController()
        val repo = repository(cellular) {
            respond("boom", HttpStatusCode.InternalServerError)
        }

        val error = assertIs<VerificationOutcome.Error>(repo.run())
        assertEquals("Something went wrong", error.message)
        assertEquals(1, cellular.resetCount)
    }

    @Test
    fun verify_should_ShowBackendErrorDescription_when_VerifyIsRejectedWithJson() = runTest {
        val cellular = FakeCellularController()
        val repo = repository(cellular) {
            respond("""{"errorDescription":"No coverage","token":"tok-1"}""", HttpStatusCode.BadRequest, jsonHeaders)
        }

        val error = assertIs<VerificationOutcome.Error>(repo.run())
        assertEquals("No coverage", error.message)
        assertEquals(1, cellular.resetCount)
    }

    @Test
    fun verify_should_SendAuthorizationWithPoll_when_Polling() = runTest {
        val pollAuthorizations = mutableListOf<String?>()
        val repo = repository(FakeCellularController()) { request ->
            when {
                request.url.encodedPath.startsWith("/napi/verify/result/") -> {
                    pollAuthorizations.add(request.headers[HttpHeaders.Authorization])
                    respond("""{"result":"VALID"}""", HttpStatusCode.OK, jsonHeaders)
                }

                else -> respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        repo.run()

        assertEquals(listOf<String?>("Bearer key-1"), pollAuthorizations)
    }

    @Test
    fun verify_should_RetryPoll_when_PollFailsWithServerError() = runTest {
        var pollCount = 0
        val repo = repository(FakeCellularController()) { request ->
            when {
                request.url.encodedPath.startsWith("/napi/verify/result/") -> {
                    pollCount++
                    if (pollCount == 1) {
                        respond("unavailable", HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond("""{"result":"VALID"}""", HttpStatusCode.OK, jsonHeaders)
                    }
                }

                else -> respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        assertIs<VerificationOutcome.Valid>(repo.run())
        assertEquals(2, pollCount)
    }

    @Test
    fun verify_should_StopPolling_when_PollFailsWithClientError() = runTest {
        var pollCount = 0
        val repo = repository(FakeCellularController()) { request ->
            when {
                request.url.encodedPath.startsWith("/napi/verify/result/") -> {
                    pollCount++
                    respond("""{"errorDescription":"Invalid request"}""", HttpStatusCode.BadRequest, jsonHeaders)
                }

                else -> respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        val error = assertIs<VerificationOutcome.Error>(repo.run())
        assertEquals("Invalid request", error.message)
        assertEquals("tok-1", error.token)
        assertEquals(1, pollCount)
    }

    @Test
    fun verify_should_ReturnResultErrorDescription_when_ResultIsError() = runTest {
        val repo = repository(FakeCellularController()) { request ->
            when {
                request.url.encodedPath.startsWith("/napi/verify/result/") ->
                    respond("""{"result":"ERROR","errorDescription":"Device timed out"}""", HttpStatusCode.OK, jsonHeaders)

                else -> respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        val error = assertIs<VerificationOutcome.Error>(repo.run())
        assertEquals("Device timed out", error.message)
    }

    @Test
    fun verify_should_ResetAndReturnErrorWithoutPolling_when_RedirectFlowFails() = runTest {
        val cellular = FakeCellularController()
        var pollCount = 0
        val repo = repository(cellular) { request ->
            when {
                request.url.host == "operator.example" ->
                    respond("operator rejected", HttpStatusCode.Forbidden)

                request.url.encodedPath.startsWith("/napi/verify/result/") -> {
                    pollCount++
                    respond("""{"result":"VALID"}""", HttpStatusCode.OK, jsonHeaders)
                }

                else -> respond(
                    """{"token":"tok-1","deviceRedirectUrl":"https://operator.example/authorize"}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
            }
        }

        val error = assertIs<VerificationOutcome.Error>(repo.run(camara = true))
        assertEquals("Redirect flow finished.\nHTTP status 403 on operator.example/authorize", error.message)
        assertEquals("tok-1", error.token)
        assertTrue(error.additionalError.orEmpty().endsWith("operator rejected"))
        assertEquals(1, cellular.resetCount)
        assertEquals(0, pollCount)
    }

    @Test
    fun verify_should_PollForResult_when_RedirectFlowEndsWithErrorOnBackend() = runTest {
        val repo = repository(FakeCellularController()) { request ->
            when {
                request.url.host == "operator.example" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(HttpHeaders.Location, "https://example.com/napi/verify/camara-authorize-complete?error=denied"),
                )

                request.url.encodedPath == "/napi/verify/camara-authorize-complete" ->
                    respond("", HttpStatusCode.BadRequest)

                request.url.encodedPath.startsWith("/napi/verify/result/") ->
                    respond("""{"result":"INVALID"}""", HttpStatusCode.OK, jsonHeaders)

                else -> respond(
                    """{"token":"tok-1","deviceRedirectUrl":"https://operator.example/authorize"}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
            }
        }

        assertIs<VerificationOutcome.Invalid>(repo.run(camara = true))
    }

    @Test
    fun verify_should_ContinueVerification_when_DeviceAddressFails() = runTest {
        val repo = repository(FakeCellularController()) { request ->
            when {
                request.url.encodedPath == "/napi/device-address" ->
                    respond("not found", HttpStatusCode.NotFound)

                request.url.encodedPath.startsWith("/napi/verify/result/") ->
                    respond("""{"result":"VALID"}""", HttpStatusCode.OK, jsonHeaders)

                else -> respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        val valid = assertIs<VerificationOutcome.Valid>(repo.run(debug = true))
        assertNull(valid.deviceIp)
    }

    @Test
    fun verify_should_FetchDeviceAddressAfterSwitchingToCellular_when_DebugEnabled() = runTest {
        val cellular = FakeCellularController()
        var switchCountAtAddressFetch = -1
        val repo = repository(cellular) { request ->
            when {
                request.url.encodedPath == "/napi/device-address" -> {
                    switchCountAtAddressFetch = cellular.switchCount
                    respond("""{"address":"1.2.3.4","port":443}""", HttpStatusCode.OK, jsonHeaders)
                }

                request.url.encodedPath.startsWith("/napi/verify/result/") ->
                    respond("""{"result":"VALID"}""", HttpStatusCode.OK, jsonHeaders)

                else -> respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        repo.run(debug = true)

        assertEquals(1, switchCountAtAddressFetch)
    }

    @Test
    fun verify_should_IncludeDeviceAddress_when_DebugEnabled() = runTest {
        val repo = repository(FakeCellularController()) { request ->
            when {
                request.url.encodedPath == "/napi/device-address" ->
                    respond("""{"address":"1.2.3.4","port":443}""", HttpStatusCode.OK, jsonHeaders)

                request.url.encodedPath.startsWith("/napi/verify/result/") ->
                    respond("""{"result":"VALID"}""", HttpStatusCode.OK, jsonHeaders)

                else -> respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        val valid = assertIs<VerificationOutcome.Valid>(repo.run(debug = true))
        assertEquals("1.2.3.4", valid.deviceIp)
        assertEquals(443, valid.devicePort)
    }
}
