package com.infobip.napi.network

import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import com.infobip.napi.model.domain.VerificationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun sendVerifyRequest_should_UseNvPathAndSendAuthorization_when_MethodIsCamara() = runTest {
        var capturedAuthorization: String? = null
        var capturedUrl = ""
        val client = mockHttpClient { request ->
            capturedUrl = request.url.toString()
            capturedAuthorization = request.headers[HttpHeaders.Authorization]
            respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
        }

        val response = ApiClient(client).sendVerifyRequest(
            domain = "https://example.com/",
            msisdn = "38591234567",
            authorization = "Bearer key-1",
            type = VerificationType.CAMARA,
        )

        assertEquals("tok-1", response.token)
        assertEquals("https://example.com/napi/verify/nv/38591234567", capturedUrl)
        assertEquals("Bearer key-1", capturedAuthorization)
    }

    @Test
    fun sendVerifyRequest_should_UseSmvPath_when_MethodIsSmv() = runTest {
        var capturedUrl = ""
        val client = mockHttpClient { request ->
            capturedUrl = request.url.toString()
            respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
        }

        ApiClient(client).sendVerifyRequest(
            domain = "https://example.com",
            msisdn = "38591234567",
            authorization = "Bearer key-1",
            type = VerificationType.SMV,
        )

        assertEquals("https://example.com/napi/verify/smv/38591234567", capturedUrl)
    }

    @Test
    fun sendVerifyRequest_should_KeepPlusSignInPath_when_MsisdnHasPlusPrefix() = runTest {
        var capturedPath = ""
        val client = mockHttpClient { request ->
            capturedPath = request.url.encodedPath
            respond("""{"token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
        }

        ApiClient(client).sendVerifyRequest(
            domain = "https://example.com",
            msisdn = "+38591234567",
            authorization = "Bearer key-1",
            type = VerificationType.SMV,
        )

        assertEquals("/napi/verify/smv/+38591234567", capturedPath)
    }

    @Test
    fun sendVerifyRequest_should_ThrowApiExceptionWithBody_when_Unauthorized() = runTest {
        val client = mockHttpClient {
            respond("nope", HttpStatusCode.Unauthorized)
        }

        val exception = assertFailsWith<ApiException> {
            ApiClient(client).sendVerifyRequest(
                domain = "https://example.com",
                msisdn = "38591234567",
                authorization = "Bearer bad",
                type = VerificationType.SMV,
                )
        }

        assertEquals(401, exception.statusCode)
        assertEquals("nope", exception.body)
        assertNull(exception.errorDescription)
    }

    @Test
    fun sendVerifyRequest_should_ExposeErrorDescription_when_BackendRejectsWithJson() = runTest {
        val client = mockHttpClient {
            respond(
                """{"errorDescription":"Camara client ID is required for Camara Number Verify"}""",
                HttpStatusCode.BadRequest,
                jsonHeaders,
            )
        }

        val exception = assertFailsWith<ApiException> {
            ApiClient(client).sendVerifyRequest(
                domain = "https://example.com",
                msisdn = "38591234567",
                authorization = "Bearer key-1",
                type = VerificationType.CAMARA,
                )
        }

        assertEquals(400, exception.statusCode)
        assertEquals("Camara client ID is required for Camara Number Verify", exception.errorDescription)
        assertFalse(exception.isServerError)
    }

    @Test
    fun pollForResult_should_ReturnNull_when_NoContent() = runTest {
        val client = mockHttpClient {
            respond("", HttpStatusCode.NoContent)
        }

        assertNull(ApiClient(client).pollForResult("https://example.com", "tok-1", "Bearer key-1"))
    }

    @Test
    fun pollForResult_should_SendAuthorizationAndParseResult_when_Ok() = runTest {
        var capturedUrl = ""
        var capturedAuthorization: String? = null
        val client = mockHttpClient { request ->
            capturedUrl = request.url.toString()
            capturedAuthorization = request.headers[HttpHeaders.Authorization]
            respond("""{"result":"VALID","token":"tok-1"}""", HttpStatusCode.OK, jsonHeaders)
        }

        val response = ApiClient(client).pollForResult("https://example.com/", "tok-1", "Bearer tok")

        assertEquals("VALID", response?.result)
        assertEquals("tok-1", response?.token)
        assertEquals("https://example.com/napi/verify/result/tok-1", capturedUrl)
        assertEquals("Bearer tok", capturedAuthorization)
    }

    @Test
    fun fetchDeviceAddress_should_ParseAddressAndPortFromPrefixedPath_when_Ok() = runTest {
        var capturedUrl = ""
        val client = mockHttpClient { request ->
            capturedUrl = request.url.toString()
            respond("""{"address":"1.2.3.4","port":443}""", HttpStatusCode.OK, jsonHeaders)
        }

        val ip = ApiClient(client).fetchDeviceAddress("https://example.com", "Bearer key-1")

        assertEquals("https://example.com/napi/device-address", capturedUrl)
        assertEquals("1.2.3.4", ip.address)
        assertEquals(443, ip.port)
    }

    @Test
    fun verifyPhoneNumberDcql_should_PostCarrierIdsAsJson_when_Called() = runTest {
        var capturedUrl = ""
        var capturedMethod: HttpMethod? = null
        var capturedContentType: ContentType? = null
        var capturedBody = ""
        var capturedAuthorization: String? = null
        val client = mockHttpClient { request ->
            capturedUrl = request.url.toString()
            capturedMethod = request.method
            capturedContentType = request.body.contentType
            capturedBody = request.body.toByteArray().decodeToString()
            capturedAuthorization = request.headers[HttpHeaders.Authorization]
            respond(
                """{"nonce":"nonce-1","carrierId":1234,"credentialRequest":"{\"digital\":{}}"}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }

        val response = ApiClient(client).verifyPhoneNumberDcql("https://example.com/", listOf(1234, 5678), "Bearer key-1")

        assertEquals("https://example.com/napi/sim-based/verify/dcql", capturedUrl)
        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals(ContentType.Application.Json, capturedContentType?.withoutParameters())
        assertEquals("""{"carrierIds":[1234,5678]}""", capturedBody)
        assertEquals("Bearer key-1", capturedAuthorization)
        assertEquals("nonce-1", response.nonce)
        assertEquals(1234, response.carrierId)
        assertEquals("""{"digital":{}}""", response.credentialRequest)
    }

    @Test
    fun verifyPhoneNumber_should_PostVpTokenAndPhoneNumber_when_Called() = runTest {
        var capturedUrl = ""
        var capturedBody = ""
        val client = mockHttpClient { request ->
            capturedUrl = request.url.toString()
            capturedBody = request.body.toByteArray().decodeToString()
            respond("""{"verified":true}""", HttpStatusCode.OK, jsonHeaders)
        }

        val response = ApiClient(client).verifyPhoneNumber("https://example.com", "vp-1", "38591234567", "Bearer key-1")

        assertEquals("https://example.com/napi/sim-based/verify", capturedUrl)
        assertEquals("""{"vpToken":"vp-1","phoneNumber":"38591234567"}""", capturedBody)
        assertEquals(true, response.verified)
    }

    @Test
    fun getPhoneNumberDcql_should_PostCarrierIds_when_Called() = runTest {
        var capturedUrl = ""
        var capturedBody = ""
        val client = mockHttpClient { request ->
            capturedUrl = request.url.toString()
            capturedBody = request.body.toByteArray().decodeToString()
            respond("""{"nonce":"nonce-1","carrierId":1234,"credentialRequest":"{}"}""", HttpStatusCode.OK, jsonHeaders)
        }

        val response = ApiClient(client).getPhoneNumberDcql("https://example.com", listOf(1234), "Bearer key-1")

        assertEquals("https://example.com/napi/sim-based/device-phone-number/dcql", capturedUrl)
        assertEquals("""{"carrierIds":[1234]}""", capturedBody)
        assertEquals("nonce-1", response.nonce)
    }

    @Test
    fun getPhoneNumber_should_PostVpTokenAndParsePhoneNumber_when_Called() = runTest {
        var capturedUrl = ""
        var capturedBody = ""
        val client = mockHttpClient { request ->
            capturedUrl = request.url.toString()
            capturedBody = request.body.toByteArray().decodeToString()
            respond("""{"phoneNumber":"+38591234567"}""", HttpStatusCode.OK, jsonHeaders)
        }

        val response = ApiClient(client).getPhoneNumber("https://example.com", "vp-1", "Bearer key-1")

        assertEquals("https://example.com/napi/sim-based/device-phone-number", capturedUrl)
        assertEquals("""{"vpToken":"vp-1"}""", capturedBody)
        assertEquals("+38591234567", response.phoneNumber)
    }

    @Test
    fun verifyPhoneNumberDcql_should_ExposeErrorDescription_when_BackendRejectsWithJson() = runTest {
        val client = mockHttpClient {
            respond("""{"errorDescription":"Credential request failed"}""", HttpStatusCode.BadRequest, jsonHeaders)
        }

        val exception = assertFailsWith<ApiException> {
            ApiClient(client).verifyPhoneNumberDcql("https://example.com", listOf(1234), "Bearer key-1")
        }

        assertEquals(400, exception.statusCode)
        assertEquals("Credential request failed", exception.errorDescription)
    }

    @Test
    fun isServerError_should_BeTrue_when_StatusIs5xx() {
        assertTrue(ApiException(503, "").isServerError)
        assertFalse(ApiException(422, "").isServerError)
    }
}
