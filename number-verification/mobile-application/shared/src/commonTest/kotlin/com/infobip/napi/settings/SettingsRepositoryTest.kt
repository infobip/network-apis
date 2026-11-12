package com.infobip.napi.settings

import com.infobip.napi.model.domain.AuthMethod
import com.infobip.napi.model.domain.AuthorizationScheme
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsRepositoryTest {

    @Test
    fun saveApiKey_should_PersistSchemeAndPublish_when_Saved() {
        val storage = MapSettings()
        val repository = SettingsRepository(storage)

        repository.saveApiKey("key-1", AuthorizationScheme.BASIC)

        assertEquals("Basic key-1", repository.state.value.authorizationHeader)
        assertEquals(AuthorizationScheme.BASIC, SettingsRepository(storage).state.value.authorizationScheme)
    }

    @Test
    fun saveApiKey_should_RemoveClientCredentialsLogin_when_LoggedInBefore() {
        val storage = MapSettings()
        val repository = SettingsRepository(storage)
        repository.saveClientCredentialsLogin("tok-1", "user", "secret")

        repository.saveApiKey("key-1", AuthorizationScheme.BEARER)

        listOf(repository.state.value, SettingsRepository(storage).state.value).forEach { state ->
            assertEquals("key-1", state.apiKey)
            assertEquals(AuthMethod.API_KEY, state.authMethod)
            assertNull(state.accessToken)
            assertNull(state.username)
            assertNull(state.password)
        }
    }

    @Test
    fun state_should_DefaultToBearerScheme_when_SavedSchemeIsUnknown() {
        val settings = MapSettings("authorization_scheme" to "UNKNOWN")

        assertEquals(AuthorizationScheme.BEARER, SettingsRepository(settings).state.value.authorizationScheme)
    }

    @Test
    fun saveClientCredentialsLogin_should_ReplaceApiKeyAndPersistCredentials_when_Saved() {
        val storage = MapSettings()
        val repository = SettingsRepository(storage)
        repository.saveApiKey("key-1", AuthorizationScheme.BEARER)

        repository.saveClientCredentialsLogin("tok-1", "user", "secret")

        assertClientCredentialsState(repository.state.value)
        assertClientCredentialsState(SettingsRepository(storage).state.value)
    }

    @Test
    fun saveAccessToken_should_PublishNewToken_when_Refreshed() {
        val repository = SettingsRepository(MapSettings())
        repository.saveClientCredentialsLogin("tok-1", "user", "secret")

        repository.saveAccessToken("tok-2")

        assertEquals("Bearer tok-2", repository.state.value.authorizationHeader)
    }

    @Test
    fun clearCredentials_should_RemoveEveryCredential_when_Called() {
        val storage = MapSettings()
        val repository = SettingsRepository(storage)
        repository.saveClientCredentialsLogin("tok-1", "user", "secret")

        repository.clearCredentials(AuthorizationScheme.BEARER)

        listOf(repository.state.value, SettingsRepository(storage).state.value).forEach { state ->
            assertNull(state.apiKey)
            assertNull(state.accessToken)
            assertNull(state.username)
            assertNull(state.password)
            assertEquals(AuthMethod.API_KEY, state.authMethod)
            assertEquals(AuthorizationScheme.BEARER, state.authorizationScheme)
        }
    }

    private fun assertClientCredentialsState(state: SettingsState) {
        assertNull(state.apiKey)
        assertEquals("tok-1", state.accessToken)
        assertEquals(AuthMethod.CLIENT_CREDENTIALS, state.authMethod)
        assertEquals("user", state.username)
        assertEquals("secret", state.password)
    }
}
