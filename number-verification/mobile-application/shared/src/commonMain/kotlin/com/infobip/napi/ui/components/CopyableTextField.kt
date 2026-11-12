package com.infobip.napi.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@Composable
fun CopyableTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        textStyle = MaterialTheme.typography.bodySmall,
        singleLine = singleLine,
        maxLines = maxLines,
        trailingIcon = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
                Text("Copy")
            }
        },
    )
}
