package com.infobip.napi.settings

import com.infobip.napi.model.domain.AuthMethod
import com.infobip.napi.model.domain.AuthorizationScheme

data class SettingsState(
    val apiKey: String? = null,
    val authorizationScheme: AuthorizationScheme = AuthorizationScheme.BEARER,
    val accessToken: String? = null,
    val authMethod: AuthMethod = AuthMethod.API_KEY,
    val username: String? = null,
    val password: String? = null,
    val debug: Boolean = false,
) {
    val hasCredential: Boolean
        get() = !apiKey.isNullOrEmpty() || !accessToken.isNullOrEmpty()

    /**
     * Value for the Authorization header sent to the backend. The API key is sent
     * with the selected scheme and wins when both credentials are present; the access token is
     * always sent as a Bearer token.
     */
    val authorizationHeader: String?
        get() = when {
            !apiKey.isNullOrEmpty() -> "${authorizationScheme.headerValue} $apiKey"
            !accessToken.isNullOrEmpty() -> "${AuthorizationScheme.BEARER.headerValue} $accessToken"
            else -> null
        }
}
