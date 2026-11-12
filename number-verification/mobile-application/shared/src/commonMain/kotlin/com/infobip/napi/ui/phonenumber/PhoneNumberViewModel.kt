package com.infobip.napi.ui.phonenumber

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.infobip.napi.config.AppConfig
import com.infobip.napi.model.domain.VerificationOutcome
import com.infobip.napi.network.AuthorizationProvider
import com.infobip.napi.repository.SimBasedRepository
import com.infobip.napi.settings.SettingsState
import com.infobip.napi.ui.verify.VerificationUiState

class PhoneNumberViewModel(
    private val simBasedRepository: SimBasedRepository,
    private val authorizationProvider: AuthorizationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<VerificationUiState>(VerificationUiState.Idle())
    val state: StateFlow<VerificationUiState> = _state.asStateFlow()

    fun submit(settings: SettingsState) {
        if (!settings.hasCredential) {
            _state.value = VerificationUiState.Idle(AUTHORIZATION_MISSING)
            return
        }

        _state.value = VerificationUiState.Submitting

        viewModelScope.launch {
            val authorization = try {
                authorizationProvider.authorizationHeader(settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = VerificationUiState.Failure(
                    VerificationOutcome.Error(
                        msisdn = "",
                        message = "Could not refresh the access token",
                        additionalError = e.message,
                    )
                )
                return@launch
            }

            if (authorization == null) {
                _state.value = VerificationUiState.Idle(AUTHORIZATION_MISSING)
                return@launch
            }

            val outcome = simBasedRepository.getPhoneNumber(
                domain = AppConfig.BACKEND_DOMAIN,
                authorization = authorization,
            )
            _state.value = when (outcome) {
                is VerificationOutcome.Error -> VerificationUiState.Failure(outcome)
                else -> VerificationUiState.Success(outcome)
            }
        }
    }

    fun consumeResult() {
        _state.value = VerificationUiState.Idle()
    }

    private companion object {
        const val AUTHORIZATION_MISSING = "Authorization not configured"
    }
}
