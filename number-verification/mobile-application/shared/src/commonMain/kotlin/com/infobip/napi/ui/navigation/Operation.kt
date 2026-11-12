package com.infobip.napi.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.graphics.vector.ImageVector

enum class Operation(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val requiresSimCredential: Boolean = false,
) {
    VERIFICATION(
        "Number verification",
        "Silent Mobile Verification and CAMARA Number Verification",
        Icons.Filled.CheckCircle,
    ),
    GET_PHONE_NUMBER(
        "Get phone number",
        "CAMARA Phone Number Share",
        Icons.Filled.Phone,
        requiresSimCredential = true,
    ),
}
