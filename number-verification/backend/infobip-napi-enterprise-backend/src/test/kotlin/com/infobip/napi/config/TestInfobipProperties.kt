package com.infobip.napi.config

import java.time.Duration

const val TEST_INFOBIP_BASE_URL = "https://api.example.com"
const val TEST_INFOBIP_AUTHORIZATION_HEADER_VALUE = "IBSSO test-token"

fun testInfobipProperties() = InfobipProperties(
    baseUrl = TEST_INFOBIP_BASE_URL,
    authorizationHeaderValue = TEST_INFOBIP_AUTHORIZATION_HEADER_VALUE,
    connectTimeout = Duration.ofSeconds(2),
    readTimeout = Duration.ofSeconds(10)
)
