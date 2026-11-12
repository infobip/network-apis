package com.infobip.napi.network

import com.infobip.napi.config.AppConfig
import com.infobip.napi.model.domain.AuthMethod
import com.infobip.napi.settings.SettingsRepository
import com.infobip.napi.settings.SettingsState
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalEncodingApi::class, ExperimentalTime::class)
class DefaultAuthorizationProviderTest {

    private var tokenRequests = 0
    private var tokenRequestUrl: String? = null

    private fun jwt(expEpochSeconds: Long, marker: String = "old"): String {
        val encoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val header = encoder.encode("""{"alg":"HS256"}""".encodeToByteArray())
        val payload = encoder.encode("""{"sub":"$marker","exp":$expEpochSeconds}""".encodeToByteArray())
        return "$header.$payload.sig"
    }

    private fun provider(
        settings: MapSettings = MapSettings(),
        issuedToken: String = jwt((Clock.System.now() + 1.hours).epochSeconds, "fresh"),
    ): Pair<DefaultAuthorizationProvider, SettingsRepository> {
        tokenRequests = 0
        val engine = MockEngine { request ->
            tokenRequests++
            tokenRequestUrl = request.url.toString()
            respond(
                """{"access_token":"$issuedToken","token_type":"Bearer","expires_in":3600}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = SettingsRepository(settings)
        return DefaultAuthorizationProvider(ClientCredentialsClient(HttpClient(engine)), repository) to repository
    }

    @Test
    fun should_UseApiKeyWithoutRefreshing_when_ApiKeyIsSet() = runTest {
        val (provider, _) = provider()

        val header = provider.authorizationHeader(
            SettingsState(apiKey = "key-1", authMethod = AuthMethod.API_KEY),
        )

        assertEquals("Bearer key-1", header)
        assertEquals(0, tokenRequests, "an API key must not trigger a token request")
    }

    @Test
    fun should_NotRefresh_when_TokenIsStillValid() = runTest {
        val valid = jwt((Clock.System.now() + 1.hours).epochSeconds)
        val (provider, _) = provider()

        val header = provider.authorizationHeader(
            SettingsState(
                accessToken = valid,
                authMethod = AuthMethod.CLIENT_CREDENTIALS,
                username = "u",
                password = "p",
            ),
        )

        assertEquals("Bearer $valid", header)
        assertEquals(0, tokenRequests)
    }

    @Test
    fun should_Refresh_when_TokenExpired() = runTest {
        val expired = jwt((Clock.System.now() - 1.hours).epochSeconds)
        val fresh = jwt((Clock.System.now() + 1.hours).epochSeconds, "fresh")
        val (provider, repository) = provider(issuedToken = fresh)

        val header = provider.authorizationHeader(
            SettingsState(
                accessToken = expired,
                authMethod = AuthMethod.CLIENT_CREDENTIALS,
                username = "u",
                password = "p",
            ),
        )

        assertEquals("Bearer $fresh", header)
        assertEquals(1, tokenRequests)
        assertEquals("${AppConfig.BACKEND_DOMAIN}/napi/oauth2/token", tokenRequestUrl)
        assertEquals(fresh, repository.state.value.accessToken, "refreshed token must be published")
    }

    @Test
    fun should_Refresh_when_TokenExpiresInsideLeeway() = runTest {
        val soon = jwt((Clock.System.now() + 2.minutes).epochSeconds)
        val (provider, _) = provider()

        provider.authorizationHeader(
            SettingsState(
                accessToken = soon,
                authMethod = AuthMethod.CLIENT_CREDENTIALS,
                username = "u",
                password = "p",
            ),
        )

        assertEquals(1, tokenRequests, "a token expiring within the leeway must be refreshed")
    }

    @Test
    fun should_NotRefresh_when_CredentialsAreMissing() = runTest {
        val expired = jwt((Clock.System.now() - 1.hours).epochSeconds)
        val (provider, _) = provider()

        val header = provider.authorizationHeader(
            SettingsState(accessToken = expired, authMethod = AuthMethod.CLIENT_CREDENTIALS),
        )

        assertEquals("Bearer $expired", header)
        assertEquals(0, tokenRequests, "cannot refresh without saved credentials")
    }

    @Test
    fun should_ReturnNull_when_NoCredentialConfigured() = runTest {
        val (provider, _) = provider()

        assertEquals(null, provider.authorizationHeader(SettingsState()))
        assertEquals(0, tokenRequests)
    }

    @Test
    fun should_PreferApiKey_when_BothSet() = runTest {
        val expired = jwt((Clock.System.now() - 1.hours).epochSeconds)
        val (provider, _) = provider()

        val header = provider.authorizationHeader(
            SettingsState(
                apiKey = "key-1",
                accessToken = expired,
                authMethod = AuthMethod.CLIENT_CREDENTIALS,
                username = "u",
                password = "p",
            ),
        )

        assertEquals("Bearer key-1", header)
        assertEquals(0, tokenRequests)
    }

    @Test
    fun should_PropagateFailure_when_RefreshIsRejected() = runTest {
        val expired = jwt((Clock.System.now() - 1.hours).epochSeconds)
        val engine = MockEngine {
            tokenRequests++
            respond("""{"error":"invalid_client"}""", HttpStatusCode.BadRequest)
        }
        val provider = DefaultAuthorizationProvider(
            ClientCredentialsClient(HttpClient(engine)),
            SettingsRepository(MapSettings()),
        )

        val error = runCatching {
            provider.authorizationHeader(
                SettingsState(
                    accessToken = expired,
                    authMethod = AuthMethod.CLIENT_CREDENTIALS,
                    username = "u",
                    password = "p",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is ApiException, "refresh failure must surface, not be swallowed")
        assertEquals(400, error.statusCode)
    }
}
