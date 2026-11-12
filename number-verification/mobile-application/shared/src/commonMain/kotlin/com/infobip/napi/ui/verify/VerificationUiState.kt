package com.infobip.napi.ui.verify

import com.infobip.napi.model.domain.VerificationOutcome

sealed interface VerificationUiState {
    data class Idle(val msisdnError: String? = null) : VerificationUiState
    data object Submitting : VerificationUiState
    data class Success(val outcome: VerificationOutcome) : VerificationUiState
    data class Failure(val outcome: VerificationOutcome.Error) : VerificationUiState
}
