package com.infobip.napi.network

import com.infobip.napi.config.AppConfig
import com.infobip.napi.model.domain.AuthMethod
import com.infobip.napi.settings.SettingsRepository
import com.infobip.napi.settings.SettingsState
import kotlin.time.Duration.Companion.minutes

/**
 * Supplies the Authorization header for outgoing requests, refreshing an access token that is
 * expired or close to it.
 */
interface AuthorizationProvider {
    /** Returns the header value to send, or null when no credential is configured. */
    suspend fun authorizationHeader(settings: SettingsState): String?
}

/**
 * Refreshes the stored access token when it is within [REFRESH_LEEWAY] of expiring. The NV flow
 * replays the credential server-to-server after an interactive operator round-trip, so a token
 * that is merely valid at the first request is not good enough.
 */
class DefaultAuthorizationProvider(
    private val clientCredentialsClient: ClientCredentialsClient,
    private val settingsRepository: SettingsRepository,
) : AuthorizationProvider {

    override suspend fun authorizationHeader(settings: SettingsState): String? {
        // An API key does not expire and takes precedence, so there is nothing to refresh.
        if (!settings.apiKey.isNullOrEmpty()) return settings.authorizationHeader

        if (settings.authMethod != AuthMethod.CLIENT_CREDENTIALS) return settings.authorizationHeader

        if (!JwtExpiry.isExpiringWithin(settings.accessToken, REFRESH_LEEWAY)) {
            return settings.authorizationHeader
        }

        val username = settings.username
        val password = settings.password
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            // Nothing to refresh with; send what we have and let the server reject it.
            return settings.authorizationHeader
        }

        val refreshed = clientCredentialsClient.requestToken(AppConfig.BACKEND_DOMAIN, username, password)
        settingsRepository.saveAccessToken(refreshed)
        return "Bearer $refreshed"
    }

    private companion object {
        /**
         * Refresh this far ahead of expiry, so the token outlives the whole interactive flow
         * rather than just the first request.
         */
        val REFRESH_LEEWAY = 5.minutes
    }
}
