package com.infobip.napi.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infobip.napi.model.domain.AuthMethod
import com.infobip.napi.model.domain.AuthorizationScheme
import com.infobip.napi.ui.components.VisibilityIcon
import com.infobip.napi.ui.components.VisibilityOffIcon

@Composable
fun AuthorizationDialog(
    initialMethod: AuthMethod,
    initialAuthorizationScheme: AuthorizationScheme,
    initialApiKey: String?,
    initialUsername: String?,
    initialPassword: String?,
    isSubmitting: Boolean,
    loginError: String?,
    onDismiss: () -> Unit,
    onSaveApiKey: (String, AuthorizationScheme) -> Unit,
    onLogin: (String, String) -> Unit,
) {
    var method by remember { mutableStateOf(initialMethod) }
    var scheme by remember { mutableStateOf(initialAuthorizationScheme) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Authorization") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AuthMethodChip(
                        label = "Username & Password",
                        selected = method == AuthMethod.CLIENT_CREDENTIALS,
                        enabled = !isSubmitting,
                        onClick = { method = AuthMethod.CLIENT_CREDENTIALS },
                        modifier = Modifier.weight(1f),
                    )
                    AuthMethodChip(
                        label = "Authorization header",
                        selected = method == AuthMethod.API_KEY,
                        enabled = !isSubmitting,
                        onClick = { method = AuthMethod.API_KEY },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                when (method) {
                    AuthMethod.CLIENT_CREDENTIALS -> ClientCredentialsForm(
                        initialUsername = initialUsername,
                        initialPassword = initialPassword,
                        isSubmitting = isSubmitting,
                        error = loginError,
                        onLogin = onLogin,
                        onCancel = onDismiss,
                    )
                    AuthMethod.API_KEY -> {
                        AuthorizationSchemeDropdown(
                            selected = scheme,
                            enabled = !isSubmitting,
                            onSelected = { scheme = it },
                        )
                        Spacer(Modifier.height(16.dp))
                        ApiKeyForm(
                            initial = initialApiKey,
                            scheme = scheme,
                            onSave = { onSaveApiKey(it, scheme); onDismiss() },
                            onCancel = onDismiss,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

@Composable
private fun AuthMethodChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        },
        enabled = enabled,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthorizationSchemeDropdown(
    selected: AuthorizationScheme,
    enabled: Boolean,
    onSelected: (AuthorizationScheme) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selected.selectionName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Authorization type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AuthorizationScheme.entries.forEach { scheme ->
                DropdownMenuItem(
                    text = { Text(scheme.selectionName) },
                    onClick = {
                        onSelected(scheme)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ApiKeyForm(
    initial: String?,
    scheme: AuthorizationScheme,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember { mutableStateOf(initial.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val spaceWarning = if (value.trim().any(Char::isWhitespace)) {
        "This value should not contain spaces. Use 1234-abcd instead of ${scheme.headerValue} 1234-abcd."
    } else {
        null
    }
    val message = error ?: spaceWarning
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; error = null },
            label = { Text("Authorization value") },
            isError = message != null,
            supportingText = message?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = {
                val trimmed = value.trim()
                if (trimmed.isEmpty()) {
                    error = "Authorization value cannot be empty"
                } else {
                    onSave(trimmed)
                }
            }) { Text("Save") }
        }
    }
}

@Composable
private fun ClientCredentialsForm(
    initialUsername: String?,
    initialPassword: String?,
    isSubmitting: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var username by remember { mutableStateOf(initialUsername.orEmpty()) }
    var password by remember { mutableStateOf(initialPassword.orEmpty()) }
    var passwordVisible by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            enabled = !isSubmitting,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !isSubmitting) {
                    Icon(
                        imageVector = if (passwordVisible) VisibilityOffIcon else VisibilityIcon,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    )
                }
            },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                TextButton(onClick = onCancel) { Text("Cancel") }
                TextButton(
                    onClick = { onLogin(username, password) },
                    enabled = username.isNotBlank() && password.isNotEmpty(),
                ) { Text("Submit") }
            }
        }
    }
}

@Composable
fun DeveloperOptionsDialog(
    initialDebug: Boolean,
    onDismiss: () -> Unit,
    onSave: (Boolean) -> Unit,
) {
    var debug by remember { mutableStateOf(initialDebug) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Developer options") },
        text = {
            Column {
                Text("Debug mode", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (debug) "Enabled" else "Disabled",
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = debug, onCheckedChange = { debug = it })
                }
                Text(
                    "Shows device IP and request ID in results",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(debug); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
