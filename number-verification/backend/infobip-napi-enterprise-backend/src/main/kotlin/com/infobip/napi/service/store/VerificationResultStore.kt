package com.infobip.napi.service.store

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import com.infobip.napi.config.VerifyProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(VerificationResultStore::class.java)

@Component
class VerificationResultStore(
    properties: VerifyProperties,
    ticker: Ticker = Ticker.systemTicker(),
) {
    private val entries: Cache<String, Entry> = Caffeine.newBuilder()
        .expireAfterWrite(properties.resultTtl)
        .maximumSize(properties.maxEntries)
        .ticker(ticker)
        .build()

    fun registerPending(token: String) {
        entries.put(token, Entry(null))
    }

    fun complete(result: VerifyResult): Boolean {
        var stored = false
        entries.asMap().computeIfPresent(result.token) { _, entry ->
            if (entry.result == null) {
                stored = true
                entry.copy(result = result)
            } else {
                entry
            }
        }
        if (!stored) {
            log.warn("Ignoring verify result for unknown or already completed token: {}", result.token)
        }
        return stored
    }

    fun find(token: String): VerifyResult? = entries.getIfPresent(token)?.result

    private data class Entry(val result: VerifyResult?)
}
