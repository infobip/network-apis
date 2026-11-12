package com.infobip.napi.settings

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import com.infobip.napi.model.domain.AuthMethod
import com.infobip.napi.model.domain.AuthorizationScheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsRepository(private val settings: Settings) {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    /** Only one credential is kept, so the client credentials login and its token are dropped. */
    fun saveApiKey(value: String, authorizationScheme: AuthorizationScheme) {
        settings[KEY_AUTH_METHOD] = AuthMethod.API_KEY.name
        settings[KEY_AUTHORIZATION_SCHEME] = authorizationScheme.name
        settings[KEY_API_KEY] = value
        settings.remove(KEY_ACCESS_TOKEN)
        settings.remove(KEY_USERNAME)
        settings.remove(KEY_PASSWORD)
        _state.update {
            it.copy(
                apiKey = value,
                authorizationScheme = authorizationScheme,
                authMethod = AuthMethod.API_KEY,
                accessToken = null,
                username = null,
                password = null,
            )
        }
    }

    /** Drops every credential; this is the app's only "log out". */
    fun clearCredentials(authorizationScheme: AuthorizationScheme) {
        settings[KEY_AUTH_METHOD] = AuthMethod.API_KEY.name
        settings[KEY_AUTHORIZATION_SCHEME] = authorizationScheme.name
        settings.remove(KEY_API_KEY)
        settings.remove(KEY_ACCESS_TOKEN)
        settings.remove(KEY_USERNAME)
        settings.remove(KEY_PASSWORD)
        _state.update {
            it.copy(
                apiKey = null,
                authorizationScheme = authorizationScheme,
                accessToken = null,
                authMethod = AuthMethod.API_KEY,
                username = null,
                password = null,
            )
        }
    }

    /** An API key takes precedence over the token, so a client credentials login replaces it. */
    fun saveClientCredentialsLogin(token: String, username: String, password: String) {
        settings.remove(KEY_API_KEY)
        settings[KEY_AUTH_METHOD] = AuthMethod.CLIENT_CREDENTIALS.name
        settings[KEY_ACCESS_TOKEN] = token
        settings[KEY_USERNAME] = username
        settings[KEY_PASSWORD] = password
        _state.update {
            it.copy(
                apiKey = null,
                accessToken = token,
                authMethod = AuthMethod.CLIENT_CREDENTIALS,
                username = username,
                password = password,
            )
        }
    }

    fun saveAccessToken(value: String) {
        settings[KEY_ACCESS_TOKEN] = value
        _state.update { it.copy(accessToken = value) }
    }

    fun saveDebug(value: Boolean) {
        settings[KEY_DEBUG] = value
        _state.update { it.copy(debug = value) }
    }

    private fun load(): SettingsState {
        val authMethod = settings.getStringOrNull(KEY_AUTH_METHOD)
            ?.let { runCatching { AuthMethod.valueOf(it) }.getOrNull() }
            ?: AuthMethod.API_KEY

        val authorizationScheme = settings.getStringOrNull(KEY_AUTHORIZATION_SCHEME)
            ?.let { runCatching { AuthorizationScheme.valueOf(it) }.getOrNull() }
            ?: AuthorizationScheme.BEARER

        return SettingsState(
            apiKey = settings.getStringOrNull(KEY_API_KEY),
            authorizationScheme = authorizationScheme,
            accessToken = settings.getStringOrNull(KEY_ACCESS_TOKEN),
            authMethod = authMethod,
            username = settings.getStringOrNull(KEY_USERNAME),
            password = settings.getStringOrNull(KEY_PASSWORD),
            debug = settings[KEY_DEBUG, false],
        )
    }

    private companion object {
        const val KEY_API_KEY = "api_key"
        const val KEY_AUTHORIZATION_SCHEME = "authorization_scheme"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_AUTH_METHOD = "auth_method"
        const val KEY_USERNAME = "client_username"
        const val KEY_PASSWORD = "client_password"
        const val KEY_DEBUG = "debug"
    }
}
