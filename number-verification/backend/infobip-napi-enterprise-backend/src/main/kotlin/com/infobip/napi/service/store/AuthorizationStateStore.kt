package com.infobip.napi.service.store

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import com.infobip.napi.config.VerifyProperties
import org.springframework.stereotype.Component

@Component
class AuthorizationStateStore(
    properties: VerifyProperties,
    ticker: Ticker = Ticker.systemTicker(),
) {
    private val states: Cache<String, VerificationRequestDetails> = Caffeine.newBuilder()
        .expireAfterWrite(properties.authorizationStateTtl)
        .maximumSize(properties.maxEntries)
        .ticker(ticker)
        .build()

    fun create(state: String, verificationRequestDetails: VerificationRequestDetails) {
        states.put(state, verificationRequestDetails)
    }

    fun consume(state: String): VerificationRequestDetails? = states.asMap().remove(state)
}

data class VerificationRequestDetails(
    val msisdn: String,
)
