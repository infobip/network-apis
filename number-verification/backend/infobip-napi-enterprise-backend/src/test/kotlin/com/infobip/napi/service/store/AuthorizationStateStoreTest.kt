package com.infobip.napi.service.store

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

private const val STATE = "state-1"

class AuthorizationStateStoreTest {

    private val ticker = FakeTicker()
    private val store = AuthorizationStateStore(testVerifyProperties(), ticker)
    private val verificationRequestDetails = VerificationRequestDetails("385911234567")

    @Test
    fun consume_should_ReturnStateOnlyOnce_when_StateCreated() {
        store.create(STATE, verificationRequestDetails)

        assertThat(store.consume(STATE)).isEqualTo(verificationRequestDetails)
        assertThat(store.consume(STATE)).isNull()
    }

    @Test
    fun consume_should_ReturnNull_when_StateUnknown() {
        assertThat(store.consume(STATE)).isNull()
    }

    @Test
    fun consume_should_ReturnNull_when_StateExpired() {
        store.create(STATE, verificationRequestDetails)

        ticker.advance(TTL.plus(Duration.ofSeconds(1)))

        assertThat(store.consume(STATE)).isNull()
    }
}
