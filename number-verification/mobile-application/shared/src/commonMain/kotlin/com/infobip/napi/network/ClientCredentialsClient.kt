package com.infobip.napi.network

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import com.infobip.napi.config.AppConfig
import com.infobip.napi.model.dto.AccessTokenResponse

/** Issues access tokens for client credentials (OAuth2 client credentials grant). */
fun interface TokenIssuer {
    suspend fun requestToken(domain: String, username: String, password: String): String
}

class ClientCredentialsClient(private val client: HttpClient) : TokenIssuer {

    /** Requests a token from the backend's OAuth2 token endpoint. */
    override suspend fun requestToken(domain: String, username: String, password: String): String {
        val response = client.submitForm(
            url = "${domain.trimEnd('/')}${AppConfig.BACKEND_API_PATH_PREFIX}/oauth2/token",
            formParameters = Parameters.build {
                append("grant_type", "client_credentials")
                append("client_id", username)
                append("client_secret", password)
            },
        )
        ensureSuccess(response)
        val token: AccessTokenResponse = networkJson.decodeFromString(response.bodyAsText())
        return token.accessToken
    }
}
