package com.infobip.napi.service.store

import com.infobip.napi.api.models.numberverify.VerifyStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

private const val TOKEN = "token-1"
private const val OTHER_TOKEN = "token-2"

class VerificationResultStoreTest {

    private val ticker = FakeTicker()
    private val store = VerificationResultStore(testVerifyProperties(), ticker)
    private val validResult = VerifyResult(TOKEN, VerifyStatus.VALID)

    @Test
    fun find_should_ReturnNull_when_ResultIsStillPending() {
        store.registerPending(TOKEN)

        assertThat(store.find(TOKEN)).isNull()
    }

    @Test
    fun find_should_ReturnSameResultOnRepeatedReads_when_ResultCompleted() {
        store.registerPending(TOKEN)

        val stored = store.complete(validResult)

        assertThat(stored).isTrue()
        assertThat(store.find(TOKEN)).isEqualTo(validResult)
        assertThat(store.find(TOKEN)).isEqualTo(validResult)
    }

    @Test
    fun complete_should_RejectResult_when_TokenWasNotRegistered() {
        val stored = store.complete(validResult)

        assertThat(stored).isFalse()
        assertThat(store.find(TOKEN)).isNull()
    }

    @Test
    fun complete_should_KeepFirstResult_when_TokenAlreadyCompleted() {
        store.registerPending(TOKEN)
        store.complete(validResult)

        val stored = store.complete(VerifyResult.error(TOKEN, "late error"))

        assertThat(stored).isFalse()
        assertThat(store.find(TOKEN)).isEqualTo(validResult)
    }

    @Test
    fun find_should_ReturnNull_when_TokenIsUnknown() {
        store.registerPending(TOKEN)
        store.complete(validResult)

        assertThat(store.find(OTHER_TOKEN)).isNull()
    }

    @Test
    fun find_should_ReturnNull_when_EntryExpired() {
        store.registerPending(TOKEN)
        store.complete(validResult)

        ticker.advance(TTL.plus(Duration.ofSeconds(1)))

        assertThat(store.find(TOKEN)).isNull()
    }

    @Test
    fun complete_should_RejectResult_when_PendingEntryExpired() {
        store.registerPending(TOKEN)
        ticker.advance(TTL.plus(Duration.ofSeconds(1)))

        assertThat(store.complete(validResult)).isFalse()
    }
}
