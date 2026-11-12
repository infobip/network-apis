package com.infobip.napi.repository

import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import com.infobip.napi.model.domain.SimCredentialError
import com.infobip.napi.model.domain.VerificationOutcome
import com.infobip.napi.network.ApiClient
import com.infobip.napi.network.SimCredentialException
import com.infobip.napi.network.mockHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimBasedRepositoryTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val domain = "https://example.com"
    private val msisdn = "38591234567"

    private val vpToken = "issuer.jwt.signature~key.binding.signature"

    private val credentialJson = """{"data":{"vp_token":{"credential-provider.example":["$vpToken"]}}}"""

    private val dcqlBody = """{"nonce":"nonce-1","carrierId":1234,"credentialRequest":"{}"}"""

    private fun repository(
        controller: FakeSimCredentialController = FakeSimCredentialController(credentialJson = credentialJson),
        handler: MockRequestHandler,
    ): SimBasedRepository = DefaultSimBasedRepository(
        apiClient = ApiClient(mockHttpClient(handler)),
        simCredentialController = controller,
    )

    private val credentialErrorJson = """{"error":"server_error","error_description":"FAILED_TO_GET_TEMP_TOKEN"}"""

    private fun simHandler(result: String): MockRequestHandler = { request ->
        when (request.url.encodedPath) {
            "/napi/sim-based/verify/dcql", "/napi/sim-based/device-phone-number/dcql" ->
                respond(dcqlBody, HttpStatusCode.OK, jsonHeaders)

            else -> respond(result, HttpStatusCode.OK, jsonHeaders)
        }
    }

    @Test
    fun verifyPhoneNumber_should_ReturnValid_when_Verified() = runTest {
        val controller = FakeSimCredentialController(credentialJson = credentialJson)
        val repo = repository(controller, simHandler("""{"verified":true}"""))

        val outcome = repo.verifyPhoneNumber(domain, msisdn, "Bearer key-1")

        val valid = assertIs<VerificationOutcome.Valid>(outcome)
        assertEquals(msisdn, valid.msisdn)
        assertNull(valid.token)
        assertEquals("nonce-1", valid.nonce)
        assertEquals("{}", controller.lastCredentialRequest)
    }

    @Test
    fun verifyPhoneNumber_should_ReturnInvalid_when_NotVerified() = runTest {
        val repo = repository(handler = simHandler("""{"verified":false}"""))

        val invalid = assertIs<VerificationOutcome.Invalid>(repo.verifyPhoneNumber(domain, msisdn, "Bearer key-1"))
        assertNull(invalid.token)
    }

    @Test
    fun getPhoneNumber_should_ReturnPhoneNumber_when_BackendReturnsIt() = runTest {
        val repo = repository(handler = simHandler("""{"phoneNumber":"+38591234567"}"""))

        val outcome = repo.getPhoneNumber(domain, "Bearer key-1")

        val phoneNumber = assertIs<VerificationOutcome.PhoneNumber>(outcome)
        assertEquals("+38591234567", phoneNumber.msisdn)
        assertNull(phoneNumber.token)
        assertEquals("nonce-1", phoneNumber.nonce)
    }

    @Test
    fun verifyPhoneNumber_should_ReturnError_when_NoCarrierIds() = runTest {
        var requestCount = 0
        val controller = FakeSimCredentialController(carrierIds = emptyList())
        val repo = repository(controller) {
            requestCount++
            respond(dcqlBody, HttpStatusCode.OK, jsonHeaders)
        }

        val error = assertIs<VerificationOutcome.Error>(repo.verifyPhoneNumber(domain, msisdn, "Bearer key-1"))

        assertEquals(SimCredentialError.NO_SIM.description, error.message)
        assertEquals(SimCredentialError.NO_SIM.description, error.additionalError)
        assertEquals(0, requestCount)
        assertEquals(0, controller.requestCount)
    }

    @Test
    fun verifyPhoneNumber_should_PassErrorDescriptionThrough_when_DcqlRejected() = runTest {
        val description = "No supported carrier (nonce: nonce-1, MI request ID: request-1): 1234 NOT_FOUND"
        val repo = repository {
            respond("""{"errorDescription":"$description"}""", HttpStatusCode.BadRequest, jsonHeaders)
        }

        val error = assertIs<VerificationOutcome.Error>(repo.verifyPhoneNumber(domain, msisdn, "Bearer key-1"))

        assertEquals(description, error.message)
        assertNull(error.nonce)
    }

    @Test
    fun verifyPhoneNumber_should_KeepNonce_when_VerifyRejected() = runTest {
        val repo = repository { request ->
            when (request.url.encodedPath) {
                "/napi/sim-based/verify/dcql" -> respond(dcqlBody, HttpStatusCode.OK, jsonHeaders)
                else -> respond(
                    """{"errorDescription":"Verify phone number failed"}""",
                    HttpStatusCode.BadRequest,
                    jsonHeaders,
                )
            }
        }

        val error = assertIs<VerificationOutcome.Error>(repo.verifyPhoneNumber(domain, msisdn, "Bearer key-1"))

        assertEquals("Verify phone number failed", error.message)
        assertEquals("nonce-1", error.nonce)
        assertNull(error.token)
        assertFalse(error.additionalError.orEmpty().contains(vpToken))
    }

    @Test
    fun getPhoneNumber_should_ReturnGenericError_when_NotFound() = runTest {
        val repo = repository { respond("", HttpStatusCode.NotFound) }

        val error = assertIs<VerificationOutcome.Error>(repo.getPhoneNumber(domain, "Bearer key-1"))

        assertEquals("Something went wrong", error.message)
        assertEquals("HTTP 404: ", error.additionalError)
        assertEquals("", error.msisdn)
    }

    @Test
    fun getPhoneNumber_should_PassErrorDescriptionThrough_when_NotFoundHasOne() = runTest {
        val repo = repository {
            respond("""{"errorDescription":"Unknown route"}""", HttpStatusCode.NotFound, jsonHeaders)
        }

        val error = assertIs<VerificationOutcome.Error>(repo.getPhoneNumber(domain, "Bearer key-1"))

        assertEquals("Unknown route", error.message)
    }

    @Test
    fun verifyPhoneNumber_should_ReturnError_when_ConsentCancelled() = runTest {
        val controller = FakeSimCredentialController(
            failure = SimCredentialException(
                SimCredentialError.CANCELLED,
                credentialError = "android.credentials.GetCredentialException.TYPE_USER_CANCELED",
            ),
        )
        val repo = repository(controller, simHandler("""{"verified":true}"""))

        val error = assertIs<VerificationOutcome.Error>(repo.verifyPhoneNumber(domain, msisdn, "Bearer key-1"))

        assertEquals(SimCredentialError.CANCELLED.description, error.message)
        assertEquals("android.credentials.GetCredentialException.TYPE_USER_CANCELED", error.additionalError)
        assertEquals("nonce-1", error.nonce)
    }

    @Test
    fun verifyPhoneNumber_should_KeepDetails_when_CredentialManagerFails() = runTest {
        val controller = FakeSimCredentialController(
            failure = SimCredentialException(SimCredentialError.ERROR, "provider failed"),
        )
        val repo = repository(controller, simHandler("""{"verified":true}"""))

        val error = assertIs<VerificationOutcome.Error>(repo.verifyPhoneNumber(domain, msisdn, "Bearer key-1"))

        assertEquals(SimCredentialError.ERROR.description, error.message)
        assertEquals("provider failed", error.additionalError)
    }

    @Test
    fun verifyPhoneNumber_should_KeepCredentialError_when_CredentialManagerFails() = runTest {
        val credentialError = "android.credentials.GetCredentialException.TYPE_NO_CREDENTIAL: No credential"
        val controller = FakeSimCredentialController(
            failure = SimCredentialException(SimCredentialError.NO_CREDENTIAL, credentialError = credentialError),
        )
        val repo = repository(controller, simHandler("""{"verified":true}"""))

        val error = assertIs<VerificationOutcome.Error>(repo.verifyPhoneNumber(domain, msisdn, "Bearer key-1"))

        assertEquals(SimCredentialError.NO_CREDENTIAL.description, error.message)
        assertEquals(credentialError, error.additionalError)
        assertEquals("nonce-1", error.nonce)
    }

    @Test
    fun verifyPhoneNumber_should_ReturnError_when_Unsupported() = runTest {
        val controller = FakeSimCredentialController(
            failure = SimCredentialException(SimCredentialError.UNSUPPORTED),
        )
        val repo = repository(controller, simHandler("""{"verified":true}"""))

        val error = assertIs<VerificationOutcome.Error>(repo.verifyPhoneNumber(domain, msisdn, "Bearer key-1"))

        assertEquals(SimCredentialError.UNSUPPORTED.description, error.message)
        assertEquals(SimCredentialError.UNSUPPORTED.description, error.additionalError)
    }

    @Test
    fun verifyPhoneNumber_should_ReturnError_when_CredentialJsonUnreadable() = runTest {
        var verifyCalled = false
        val controller = FakeSimCredentialController(credentialJson = """{"data":{}}""")
        val repo = repository(controller) { request ->
            if (request.url.encodedPath == "/napi/sim-based/verify") verifyCalled = true
            respond(dcqlBody, HttpStatusCode.OK, jsonHeaders)
        }

        val error = assertIs<VerificationOutcome.Error>(repo.verifyPhoneNumber(domain, msisdn, "Bearer key-1"))

        assertEquals("Unable to read the credential from the SIM", error.message)
        assertNull(error.additionalError)
        assertFalse(verifyCalled)
    }

    @Test
    fun verifyPhoneNumber_should_ShowCredentialError_when_CredentialJsonHoldsError() = runTest {
        var verifyCalled = false
        val controller = FakeSimCredentialController(
            credentialJson = credentialErrorJson,
        )
        val repo = repository(controller) { request ->
            if (request.url.encodedPath == "/napi/sim-based/verify") verifyCalled = true
            respond(dcqlBody, HttpStatusCode.OK, jsonHeaders)
        }

        val error = assertIs<VerificationOutcome.Error>(repo.verifyPhoneNumber(domain, msisdn, "Bearer key-1"))

        assertEquals(SimCredentialError.ERROR.description, error.message)
        assertEquals("Credential Manager returned an error: $credentialErrorJson", error.additionalError)
        assertEquals("nonce-1", error.nonce)
        assertFalse(verifyCalled)
    }

    @Test
    fun getPhoneNumber_should_ShowCredentialError_when_CredentialJsonHoldsError() = runTest {
        val controller = FakeSimCredentialController(
            credentialJson = credentialErrorJson,
        )
        val repo = repository(controller, simHandler("""{"phoneNumber":"+38591234567"}"""))

        val error = assertIs<VerificationOutcome.Error>(repo.getPhoneNumber(domain, "Bearer key-1"))

        assertEquals(SimCredentialError.ERROR.description, error.message)
        assertEquals("Credential Manager returned an error: $credentialErrorJson", error.additionalError)
        assertEquals("nonce-1", error.nonce)
    }

    @Test
    fun verifyPhoneNumber_should_Rethrow_when_Cancelled() = runTest {
        val repo = repository { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { repo.verifyPhoneNumber(domain, msisdn, "Bearer key-1") }
    }

    @Test
    fun getPhoneNumber_should_ReturnError_when_PhoneNumberMissing() = runTest {
        val repo = repository(handler = simHandler("{}"))

        val error = assertIs<VerificationOutcome.Error>(repo.getPhoneNumber(domain, "Bearer key-1"))

        assertTrue(error.message.isNotBlank())
        assertEquals("nonce-1", error.nonce)
    }
}
