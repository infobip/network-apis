package com.infobip.napi.network

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalEncodingApi::class, ExperimentalTime::class)
class JwtExpiryTest {

    private fun jwt(expEpochSeconds: Long): String {
        val encoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val header = encoder.encode("""{"alg":"HS256","typ":"JWT"}""".encodeToByteArray())
        val payload = encoder.encode("""{"sub":"mi_test","exp":$expEpochSeconds}""".encodeToByteArray())
        return "$header.$payload.signature"
    }

    @Test
    fun expiresAt_should_ReadExpClaim() {
        val exp = 1790151104L

        assertEquals(exp, JwtExpiry.expiresAt(jwt(exp))?.epochSeconds)
    }

    @Test
    fun expiresAt_should_ReturnNull_when_NotAJwt() {
        assertNull(JwtExpiry.expiresAt("plain-api-key"))
    }

    @Test
    fun expiresAt_should_ReturnNull_when_PayloadIsNotBase64() {
        assertNull(JwtExpiry.expiresAt("header.!!!not-base64!!!.signature"))
    }

    @Test
    fun expiresAt_should_ReturnNull_when_ExpClaimIsMissing() {
        val encoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val payload = encoder.encode("""{"sub":"mi_test"}""".encodeToByteArray())

        assertNull(JwtExpiry.expiresAt("header.$payload.signature"))
    }

    @Test
    fun isExpiringWithin_should_BeTrue_when_TokenIsNull() {
        assertTrue(JwtExpiry.isExpiringWithin(null, 5.minutes))
    }

    @Test
    fun isExpiringWithin_should_BeTrue_when_TokenIsNotAJwt() {
        assertTrue(JwtExpiry.isExpiringWithin("plain-api-key", 5.minutes))
    }

    @Test
    fun isExpiringWithin_should_BeTrue_when_AlreadyExpired() {
        val expired = jwt((Clock.System.now() - 1.hours).epochSeconds)

        assertTrue(JwtExpiry.isExpiringWithin(expired, 5.minutes))
    }

    @Test
    fun isExpiringWithin_should_BeTrue_when_ExpiringInsideLeeway() {
        val soon = jwt((Clock.System.now() + 2.minutes).epochSeconds)

        assertTrue(JwtExpiry.isExpiringWithin(soon, 5.minutes))
    }

    @Test
    fun isExpiringWithin_should_BeFalse_when_ExpiringBeyondLeeway() {
        val later = jwt((Clock.System.now() + 1.hours).epochSeconds)

        assertFalse(JwtExpiry.isExpiringWithin(later, 5.minutes))
    }
}
