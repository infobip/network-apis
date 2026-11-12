package com.infobip.napi.network

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Reads the expiry out of an access token. The token is the source of truth, so a token
 * stored by an earlier run is handled the same as a freshly issued one.
 */
object JwtExpiry {

    /** Returns the `exp` claim, or null when the value is not a JWT or carries no usable expiry. */
    @OptIn(ExperimentalEncodingApi::class, ExperimentalTime::class)
    fun expiresAt(token: String): Instant? {
        val segments = token.split('.')
        if (segments.size != 3) return null

        val payload = segments[1].ifEmpty { return null }
        val json = runCatching {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(payload)
                .decodeToString()
        }.getOrNull() ?: return null

        val exp = EXP_CLAIM.find(json)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return Instant.fromEpochSeconds(exp)
    }

    /**
     * True when the token is missing, unreadable, already expired, or expires within [leeway].
     * An unreadable token is treated as expiring so the caller refreshes rather than sending
     * something that cannot be checked.
     */
    @OptIn(ExperimentalTime::class)
    fun isExpiringWithin(token: String?, leeway: Duration): Boolean {
        if (token.isNullOrEmpty()) return true
        val expiresAt = expiresAt(token) ?: return true
        return expiresAt - Clock.System.now() <= leeway
    }

    private val EXP_CLAIM = """"exp"\s*:\s*(\d+)""".toRegex()
}
