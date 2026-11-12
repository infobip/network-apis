package com.infobip.napi.config

import kotlin.time.Duration.Companion.seconds

object AppConfig {
    val requestTimeout = 10.seconds
    const val POLLING_RETRY_LIMIT = 15
    val pollingRetryInterval = 2.seconds
    const val MAX_REDIRECTS = 10
    const val BACKEND_DOMAIN = BuildConfig.BACKEND_DOMAIN
    const val BACKEND_API_PATH_PREFIX = "/napi"
}
