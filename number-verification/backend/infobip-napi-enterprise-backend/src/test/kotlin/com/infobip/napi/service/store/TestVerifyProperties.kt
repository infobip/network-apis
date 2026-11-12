package com.infobip.napi.service.store

import com.infobip.napi.config.VerifyProperties
import java.time.Duration

const val TEST_BACKEND_DOMAIN = "https://backend.example.com"
val TTL: Duration = Duration.ofMinutes(5)

fun testVerifyProperties(
    usingProxy: Boolean = false,
    camaraClientId: String? = null
) = VerifyProperties(
    domain = TEST_BACKEND_DOMAIN,
    usingProxy = usingProxy,
    resultTtl = TTL,
    authorizationStateTtl = TTL,
    maxEntries = 10_000,
    camaraClientId = camaraClientId
)
