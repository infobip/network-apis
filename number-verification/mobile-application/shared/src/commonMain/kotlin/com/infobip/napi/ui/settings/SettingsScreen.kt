package com.infobip.napi.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.infobip.napi.model.domain.AuthMethod
import com.infobip.napi.model.domain.AuthorizationScheme
import com.infobip.napi.settings.SettingsState

private enum class ActiveDialog { NONE, AUTH, DEVELOPER_OPTIONS }

@Composable
fun SettingsScreen(
    settings: SettingsState,
    loginState: LoginState,
    onUpdateApiKey: (String, AuthorizationScheme) -> Unit,
    onLogin: (String, String) -> Unit,
    onResetLoginState: () -> Unit,
    onUpdateDebug: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf(ActiveDialog.NONE) }

    val authorizationSubtitle = when {
        !settings.hasCredential -> "Not set"
        !settings.apiKey.isNullOrEmpty() -> "${settings.authorizationScheme.selectionName} set"
        settings.authMethod == AuthMethod.CLIENT_CREDENTIALS -> "Username & Password set"
        else -> "Set"
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsRow(
            icon = Icons.Filled.Lock,
            title = "Authorization",
            subtitle = authorizationSubtitle,
            onClick = {
                onResetLoginState()
                dialog = ActiveDialog.AUTH
            },
        )
        HorizontalDivider()
        SettingsRow(
            icon = Icons.Filled.Build,
            title = "Developer options",
            subtitle = if (settings.debug) "Debug mode enabled" else "Debug mode disabled",
            onClick = { dialog = ActiveDialog.DEVELOPER_OPTIONS },
        )
        HorizontalDivider()
    }

    when (dialog) {
        ActiveDialog.AUTH -> {
            LaunchedEffect(loginState) {
                if (loginState is LoginState.Success) {
                    dialog = ActiveDialog.NONE
                    onResetLoginState()
                }
            }
            AuthorizationDialog(
                initialMethod = if (settings.hasCredential) settings.authMethod else AuthMethod.CLIENT_CREDENTIALS,
                initialAuthorizationScheme = settings.authorizationScheme,
                initialApiKey = settings.apiKey,
                initialUsername = settings.username,
                initialPassword = settings.password,
                isSubmitting = loginState is LoginState.Submitting,
                loginError = (loginState as? LoginState.Error)?.message,
                onDismiss = {
                    dialog = ActiveDialog.NONE
                    onResetLoginState()
                },
                onSaveApiKey = onUpdateApiKey,
                onLogin = onLogin,
            )
        }

        ActiveDialog.DEVELOPER_OPTIONS -> DeveloperOptionsDialog(
            initialDebug = settings.debug,
            onDismiss = { dialog = ActiveDialog.NONE },
            onSave = onUpdateDebug,
        )

        ActiveDialog.NONE -> Unit
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, maxLines = 1) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
    )
}
