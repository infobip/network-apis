package com.infobip.napi.ui.verify

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.infobip.napi.model.domain.VerifyMethod
import com.infobip.napi.settings.SettingsState
import com.infobip.napi.ui.components.MissingAuthorizationWarning
import com.infobip.napi.ui.components.VerifyButton

@Composable
fun VerificationScreen(
    settings: SettingsState,
    uiState: VerificationUiState,
    supportsAutoCellular: Boolean,
    supportsSimCredential: Boolean,
    initialMsisdn: String,
    onSubmit: (String, VerifyMethod) -> Unit,
    onMsisdnChange: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var msisdn by remember(initialMsisdn) { mutableStateOf(initialMsisdn) }
    val enabled = settings.hasCredential
    val msisdnError = (uiState as? VerificationUiState.Idle)?.msisdnError
    val focusManager = LocalFocusManager.current

    // The iOS phone keypad has no return key, so tapping outside the field is the way to close it.
    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (!enabled) {
            MissingAuthorizationWarning(onOpenSettings = onOpenSettings, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }

        if (!supportsAutoCellular) {
            ManualCellularNote()
            Spacer(Modifier.height(16.dp))
        }

        Text(
            "Check with the carrier that a phone number matches the one associated with the mobile device.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = msisdn,
            onValueChange = {
                msisdn = it
                onMsisdnChange()
            },
            label = { Text("Phone number (MSISDN)") },
            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            isError = msisdnError != null,
            supportingText = msisdnError?.let { { Text(it) } },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(32.dp))

        VerifyButton(
            text = "Verify with SMV",
            enabled = enabled,
            onClick = {
                focusManager.clearFocus()
                onSubmit(msisdn, VerifyMethod.SMV)
            },
        )

        Spacer(Modifier.height(16.dp))

        VerifyButton(
            text = "Verify with Network-based CAMARA NV",
            enabled = enabled,
            onClick = {
                focusManager.clearFocus()
                onSubmit(msisdn, VerifyMethod.CAMARA)
            },
        )

        if (supportsSimCredential) {
            Spacer(Modifier.height(16.dp))

            VerifyButton(
                text = "Verify with SIM-based CAMARA NV",
                enabled = enabled,
                onClick = {
                    focusManager.clearFocus()
                    onSubmit(msisdn, VerifyMethod.SIM_BASED)
                },
            )
        }
    }
}

@Composable
private fun ManualCellularNote() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Text(
                "Automatic switch to cellular is not possible on this platform. Connect to mobile data manually before verifying.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
