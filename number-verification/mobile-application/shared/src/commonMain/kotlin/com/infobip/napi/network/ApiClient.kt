package com.infobip.napi.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import com.infobip.napi.config.AppConfig
import com.infobip.napi.model.domain.VerificationType
import com.infobip.napi.model.dto.ClientVerifyResponse
import com.infobip.napi.model.dto.ClientVerifyResultResponse
import com.infobip.napi.model.dto.DcqlRequest
import com.infobip.napi.model.dto.DcqlResponse
import com.infobip.napi.model.dto.ErrorResponse
import com.infobip.napi.model.dto.GetPhoneNumberRequest
import com.infobip.napi.model.dto.GetPhoneNumberResponse
import com.infobip.napi.model.dto.IpAddress
import com.infobip.napi.model.dto.VerifyPhoneNumberRequest
import com.infobip.napi.model.dto.VerifyPhoneNumberResponse

class ApiClient(private val client: HttpClient) {

    suspend fun sendVerifyRequest(
        domain: String,
        msisdn: String,
        authorization: String,
        type: VerificationType,
    ): ClientVerifyResponse {
        val response = client.get(url(domain, "/verify/${type.pathSegment}/${msisdn.encodeURLPathPart()}")) {
            header(HttpHeaders.Authorization, authorization)
        }
        ensureSuccess(response)
        return networkJson.decodeFromString(response.bodyAsText())
    }

    /**
     * The backend only returns results to authorized callers, so the poll carries the same
     * credential as the verify request.
     */
    suspend fun pollForResult(domain: String, token: String, authorization: String): ClientVerifyResultResponse? {
        val response = client.get(url(domain, "/verify/result/${token.encodeURLPathPart()}")) {
            header(HttpHeaders.Authorization, authorization)
        }
        if (response.status == HttpStatusCode.NoContent) return null
        ensureSuccess(response)
        return networkJson.decodeFromString(response.bodyAsText())
    }

    suspend fun fetchDeviceAddress(domain: String, authorization: String): IpAddress {
        val response = client.get(url(domain, "/device-address")) {
            header(HttpHeaders.Authorization, authorization)
        }
        ensureSuccess(response)
        return networkJson.decodeFromString(response.bodyAsText())
    }

    suspend fun verifyPhoneNumberDcql(domain: String, carrierIds: List<Int>, authorization: String): DcqlResponse {
        val body = networkJson.encodeToString(DcqlRequest(carrierIds))
        val response = post(domain, "/sim-based/verify/dcql", body, authorization)
        ensureSuccess(response)
        return networkJson.decodeFromString(response.bodyAsText())
    }

    suspend fun verifyPhoneNumber(
        domain: String,
        vpToken: String,
        phoneNumber: String,
        authorization: String,
    ): VerifyPhoneNumberResponse {
        val body = networkJson.encodeToString(VerifyPhoneNumberRequest(vpToken, phoneNumber))
        val response = post(domain, "/sim-based/verify", body, authorization)
        ensureSuccess(response)
        return networkJson.decodeFromString(response.bodyAsText())
    }

    suspend fun getPhoneNumberDcql(domain: String, carrierIds: List<Int>, authorization: String): DcqlResponse {
        val body = networkJson.encodeToString(DcqlRequest(carrierIds))
        val response = post(domain, "/sim-based/device-phone-number/dcql", body, authorization)
        ensureSuccess(response)
        return networkJson.decodeFromString(response.bodyAsText())
    }

    suspend fun getPhoneNumber(domain: String, vpToken: String, authorization: String): GetPhoneNumberResponse {
        val body = networkJson.encodeToString(GetPhoneNumberRequest(vpToken))
        val response = post(domain, "/sim-based/device-phone-number", body, authorization)
        ensureSuccess(response)
        return networkJson.decodeFromString(response.bodyAsText())
    }

    private suspend fun post(domain: String, path: String, body: String, authorization: String): HttpResponse =
        client.post(url(domain, path)) {
            header(HttpHeaders.Authorization, authorization)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun url(domain: String, path: String): String =
        "${domain.trimEnd('/')}${AppConfig.BACKEND_API_PATH_PREFIX}$path"
}

internal suspend fun ensureSuccess(response: HttpResponse) {
    if (response.status.value !in 200..299) {
        throw ApiException(response.status.value, response.bodyAsText())
    }
}

class ApiException(val statusCode: Int, val body: String) :
    Exception("HTTP $statusCode: $body") {

    /** The backend reports rejected requests as JSON carrying an `errorDescription`. */
    val errorDescription: String?
        get() = runCatching { networkJson.decodeFromString<ErrorResponse>(body).errorDescription }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    val isServerError: Boolean
        get() = statusCode >= 500
}
