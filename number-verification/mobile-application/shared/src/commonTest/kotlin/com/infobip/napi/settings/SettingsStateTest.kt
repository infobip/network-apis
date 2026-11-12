package com.infobip.napi.settings

import com.infobip.napi.model.domain.AuthorizationScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsStateTest {

    @Test
    fun authorizationHeader_should_UseAppScheme_when_OnlyApiKeyIsSet() {
        val state = SettingsState(apiKey = "key-1")

        assertEquals("Bearer key-1", state.authorizationHeader)
    }

    @Test
    fun authorizationHeader_should_UseSelectedScheme_when_ApiKeyIsSet() {
        AuthorizationScheme.entries.forEach { scheme ->
            val state = SettingsState(apiKey = "key-1", authorizationScheme = scheme)

            assertEquals("${scheme.headerValue} key-1", state.authorizationHeader)
        }
    }

    @Test
    fun authorizationHeader_should_UseBearerScheme_when_OnlyAccessTokenIsSet() {
        val state = SettingsState(accessToken = "tok-1", authorizationScheme = AuthorizationScheme.BASIC)

        assertEquals("Bearer tok-1", state.authorizationHeader)
    }

    @Test
    fun authorizationHeader_should_PreferApiKey_when_BothAreSet() {
        val state = SettingsState(apiKey = "key-1", accessToken = "tok-1")

        assertEquals("Bearer key-1", state.authorizationHeader)
    }

    @Test
    fun authorizationHeader_should_BeNull_when_NoCredentialIsSet() {
        assertNull(SettingsState().authorizationHeader)
    }

    @Test
    fun authorizationHeader_should_IgnoreEmptyApiKey_when_AccessTokenIsSet() {
        val state = SettingsState(apiKey = "", accessToken = "tok-1")

        assertEquals("Bearer tok-1", state.authorizationHeader)
    }

    @Test
    fun hasCredential_should_BeTrue_when_OnlyAccessTokenIsSet() {
        assertTrue(SettingsState(accessToken = "tok-1").hasCredential)
    }

    @Test
    fun hasCredential_should_BeFalse_when_NoCredentialIsSet() {
        assertFalse(SettingsState().hasCredential)
    }
}
