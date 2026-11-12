package com.infobip.napi.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import com.infobip.napi.config.AppConfig

fun createHttpClient(): HttpClient {
    val timeoutMillis = AppConfig.requestTimeout.inWholeMilliseconds
    return HttpClient(createHttpClientEngine()) {
        expectSuccess = false
        followRedirects = false

        install(HttpTimeout) {
            requestTimeoutMillis = timeoutMillis
            connectTimeoutMillis = timeoutMillis
            socketTimeoutMillis = timeoutMillis
        }
    }
}
