package com.infobip.napi.config

import java.time.Duration

const val TEST_CLIENT_ID = "test-client"
const val TEST_CLIENT_SECRET = "test-client-secret"
const val TEST_SECOND_CLIENT_ID = "second-test-client"
const val TEST_SECOND_CLIENT_SECRET = "second-test-client-secret"
val TEST_TOKEN_TTL: Duration = Duration.ofHours(1)

fun testAuthProperties() = AuthProperties(
    tokenSecret = "test-token-secret",
    tokenTtl = TEST_TOKEN_TTL,
    clients = setOf(
        AuthProperties.ClientCredentials(TEST_CLIENT_ID, TEST_CLIENT_SECRET),
        AuthProperties.ClientCredentials(TEST_SECOND_CLIENT_ID, TEST_SECOND_CLIENT_SECRET)
    )
)
