package com.infobip.napi.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.infobip.napi.config.AppConfig
import com.infobip.napi.model.domain.VerificationOutcome
import com.infobip.napi.model.domain.VerificationType
import com.infobip.napi.model.domain.VerifyMethod
import com.infobip.napi.network.AuthorizationProvider
import com.infobip.napi.repository.SimBasedRepository
import com.infobip.napi.repository.VerificationRepository
import com.infobip.napi.settings.SettingsState
import com.infobip.napi.util.MsisdnValidator

class VerificationViewModel(
    private val repository: VerificationRepository,
    private val simBasedRepository: SimBasedRepository,
    private val authorizationProvider: AuthorizationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<VerificationUiState>(VerificationUiState.Idle())
    val state: StateFlow<VerificationUiState> = _state.asStateFlow()

    fun submit(msisdn: String, method: VerifyMethod, settings: SettingsState) {
        val msisdnError = MsisdnValidator.validate(msisdn)
        if (msisdnError != null) {
            _state.value = VerificationUiState.Idle(msisdnError)
            return
        }

        if (!settings.hasCredential) {
            _state.value = VerificationUiState.Idle(AUTHORIZATION_MISSING)
            return
        }

        _state.value = VerificationUiState.Submitting

        viewModelScope.launch {
            // Refresh an expiring access token first; the NV flow replays this credential
            // server-to-server after the operator redirect chain, long after the first request.
            val authorization = try {
                authorizationProvider.authorizationHeader(settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = VerificationUiState.Failure(
                    VerificationOutcome.Error(
                        msisdn = msisdn,
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

            suspend fun verify(type: VerificationType) = repository.verify(
                domain = AppConfig.BACKEND_DOMAIN,
                msisdn = msisdn,
                authorization = authorization,
                type = type,
                debugEnabled = settings.debug,
            )

            val outcome = when (method) {
                VerifyMethod.SMV -> verify(VerificationType.SMV)
                VerifyMethod.CAMARA -> verify(VerificationType.CAMARA)
                VerifyMethod.SIM_BASED -> simBasedRepository.verifyPhoneNumber(
                    domain = AppConfig.BACKEND_DOMAIN,
                    msisdn = msisdn,
                    authorization = authorization,
                )
            }
            _state.value = when (outcome) {
                is VerificationOutcome.Error -> VerificationUiState.Failure(outcome)
                else -> VerificationUiState.Success(outcome)
            }
        }
    }

    fun clearMsisdnError() {
        val current = _state.value
        if (current is VerificationUiState.Idle && current.msisdnError != null) {
            _state.value = VerificationUiState.Idle()
        }
    }

    fun consumeResult() {
        _state.value = VerificationUiState.Idle()
    }

    private companion object {
        const val AUTHORIZATION_MISSING = "Authorization not configured"
    }
}
