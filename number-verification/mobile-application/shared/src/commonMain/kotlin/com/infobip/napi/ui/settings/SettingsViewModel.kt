package com.infobip.napi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.infobip.napi.config.AppConfig
import com.infobip.napi.model.domain.AuthorizationScheme
import com.infobip.napi.network.ApiException
import com.infobip.napi.network.TokenIssuer
import com.infobip.napi.settings.SettingsRepository
import com.infobip.napi.settings.SettingsState

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val tokenIssuer: TokenIssuer,
) : ViewModel() {

    val state: StateFlow<SettingsState> = repository.state

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    fun updateApiKey(value: String, authorizationScheme: AuthorizationScheme) {
        // No credential contains whitespace, so a pasted "Bearer 1234-abc" keeps only the value after the last gap.
        val trimmed = value.trim().split(Regex("\\s+")).last()
        if (trimmed.isEmpty()) {
            repository.clearCredentials(authorizationScheme)
        } else {
            repository.saveApiKey(trimmed, authorizationScheme)
        }
    }

    fun updateDebug(value: Boolean) {
        repository.saveDebug(value)
    }

    fun login(username: String, password: String) {
        val trimmedUsername = username.trim()
        if (trimmedUsername.isEmpty() || password.isEmpty()) {
            _loginState.value = LoginState.Error("Username and password are required")
            return
        }
        _loginState.value = LoginState.Submitting
        viewModelScope.launch {
            try {
                val token = tokenIssuer.requestToken(AppConfig.BACKEND_DOMAIN, trimmedUsername, password)
                repository.saveClientCredentialsLogin(token, trimmedUsername, password)
                _loginState.value = LoginState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                // The token endpoint error body names the cause (invalid_client, unsupported_grant_type, ...),
                // so surface it instead of showing only the status code.
                val detail = e.body.takeIf { it.isNotBlank() }
                _loginState.value = LoginState.Error(
                    if (detail != null) "Login failed (HTTP ${e.statusCode}): $detail"
                    else "Login failed (HTTP ${e.statusCode})"
                )
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }
}

sealed interface LoginState {
    data object Idle : LoginState
    data object Submitting : LoginState
    data object Success : LoginState
    data class Error(val message: String) : LoginState
}
