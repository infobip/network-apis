package com.infobip.napi.model.domain

enum class AuthorizationScheme(val headerValue: String, val selectionName: String) {
    BASIC("Basic", "Basic"),
    BEARER("Bearer", "oAuth 2"),
}
