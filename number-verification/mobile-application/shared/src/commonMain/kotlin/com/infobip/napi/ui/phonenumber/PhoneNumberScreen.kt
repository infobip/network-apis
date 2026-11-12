package com.infobip.napi.ui.phonenumber

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infobip.napi.settings.SettingsState
import com.infobip.napi.ui.components.MissingAuthorizationWarning
import com.infobip.napi.ui.components.VerifyButton
import com.infobip.napi.ui.verify.VerificationUiState

@Composable
fun PhoneNumberScreen(
    settings: SettingsState,
    uiState: VerificationUiState,
    onSubmit: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = settings.hasCredential
    val error = (uiState as? VerificationUiState.Idle)?.msisdnError

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (!enabled) {
            MissingAuthorizationWarning(onOpenSettings = onOpenSettings, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }

        Text(
            "Get the phone number of the SIM in this device from the carrier.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(24.dp))

        VerifyButton(
            text = "Get phone number",
            enabled = enabled,
            onClick = onSubmit,
        )

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
