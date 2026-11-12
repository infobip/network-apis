package com.infobip.napi.service.store

import com.github.benmanes.caffeine.cache.Ticker
import java.time.Duration

class FakeTicker : Ticker {
    private var nanos = 0L

    override fun read(): Long = nanos

    fun advance(duration: Duration) {
        nanos += duration.toNanos()
    }
}
