package com.infobip.napi.ui.settings

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.infobip.napi.config.AppConfig
import com.infobip.napi.model.domain.AuthMethod
import com.infobip.napi.model.domain.AuthorizationScheme
import com.infobip.napi.network.ApiException
import com.infobip.napi.network.TokenIssuer
import com.infobip.napi.settings.SettingsRepository
import com.infobip.napi.settings.SettingsState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private var tokenRequestDomain: String? = null

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun issuingToken(token: String = "tok-1") = TokenIssuer { domain, _, _ ->
        tokenRequestDomain = domain
        token
    }

    private fun rejectingToken() = TokenIssuer { domain, _, _ ->
        tokenRequestDomain = domain
        throw ApiException(401, """{"error":"invalid_client"}""")
    }

    private val storage = MapSettings()

    private fun persisted(): SettingsState = SettingsRepository(storage).state.value

    @Test
    fun updateApiKey_should_StoreValueWithScheme_when_ValueProvided() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(SettingsRepository(storage), issuingToken())

        viewModel.updateApiKey(" key-1 ", AuthorizationScheme.BASIC)

        assertEquals("Basic key-1", viewModel.state.value.authorizationHeader)
        val reloaded = persisted()
        assertEquals("key-1", reloaded.apiKey)
        assertEquals(AuthorizationScheme.BASIC, reloaded.authorizationScheme)
    }

    @Test
    fun updateApiKey_should_DropEverythingBeforeLastSpace_when_ValueContainsPrefix() = runTest(dispatcher) {
        listOf("Bearer 1234-abc", "  Bearer   1234-abc  ", "Basic Bearer 1234-abc", "Bearer\t1234-abc").forEach { input ->
            val viewModel = SettingsViewModel(SettingsRepository(storage), issuingToken())

            viewModel.updateApiKey(input, AuthorizationScheme.BEARER)

            assertEquals("Bearer 1234-abc", viewModel.state.value.authorizationHeader, "input: '$input'")
            assertEquals("1234-abc", persisted().apiKey, "input: '$input'")
        }
    }

    @Test
    fun updateApiKey_should_ClearAllCredentials_when_ValueEmpty() = runTest(dispatcher) {
        val repository = SettingsRepository(storage)
        repository.saveClientCredentialsLogin("tok-1", "u", "p")
        val viewModel = SettingsViewModel(repository, issuingToken())

        viewModel.updateApiKey("", AuthorizationScheme.BEARER)

        assertNull(viewModel.state.value.authorizationHeader)
        val reloaded = persisted()
        assertNull(reloaded.accessToken)
        assertNull(reloaded.username)
    }

    @Test
    fun login_should_RequestTokenFromBackendDomainAndReplaceApiKey_when_CredentialsValid() = runTest(dispatcher) {
        val repository = SettingsRepository(storage)
        repository.saveApiKey("old-key", AuthorizationScheme.BEARER)
        val viewModel = SettingsViewModel(repository, issuingToken())

        viewModel.login("user", "secret")
        advanceUntilIdle()

        assertEquals(AppConfig.BACKEND_DOMAIN, tokenRequestDomain)
        assertIs<LoginState.Success>(viewModel.loginState.value)
        val state = viewModel.state.value
        assertNull(state.apiKey)
        assertEquals(AuthMethod.CLIENT_CREDENTIALS, state.authMethod)
        assertEquals("Bearer tok-1", state.authorizationHeader)
        val reloaded = persisted()
        assertEquals("tok-1", reloaded.accessToken)
        assertNull(reloaded.apiKey)
        assertEquals("user", reloaded.username)
    }

    @Test
    fun login_should_SurfaceTokenErrorBody_when_TokenRequestRejected() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(SettingsRepository(storage), rejectingToken())

        viewModel.login("user", "wrong")
        advanceUntilIdle()

        val error = assertIs<LoginState.Error>(viewModel.loginState.value)
        assertEquals("""Login failed (HTTP 401): {"error":"invalid_client"}""", error.message)
    }

    @Test
    fun state_should_ReflectRepositoryWrites_when_TokenRefreshedOutsideViewModel() = runTest(dispatcher) {
        val repository = SettingsRepository(storage)
        repository.saveClientCredentialsLogin("tok-old", "u", "p")
        val viewModel = SettingsViewModel(repository, issuingToken())

        repository.saveAccessToken("tok-new")

        assertEquals("Bearer tok-new", viewModel.state.value.authorizationHeader)
    }
}
