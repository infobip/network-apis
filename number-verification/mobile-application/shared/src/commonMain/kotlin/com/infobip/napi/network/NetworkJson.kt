package com.infobip.napi.network

import kotlinx.serialization.json.Json

val networkJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}
