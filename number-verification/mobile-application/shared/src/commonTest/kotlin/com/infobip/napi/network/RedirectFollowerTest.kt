package com.infobip.napi.network

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import com.infobip.napi.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BACKEND_DOMAIN = "https://backend.example.com"

class RedirectFollowerTest {

    @Test
    fun follow_should_StopOnTerminalResponse_when_SingleHop() = runTest {
        var requestCount = 0
        val client = mockHttpClient {
            requestCount++
            respond("", HttpStatusCode.OK)
        }

        val result = RedirectFollower(client).follow("https://example.com/start", BACKEND_DOMAIN)

        assertEquals(1, requestCount)
        assertTrue(result.isSuccess)
        assertEquals("Redirect flow finished.\nHTTP status 200 on example.com/start", result.message)
    }

    @Test
    fun follow_should_ForwardSetCookieAsCookie_when_RedirectedToSameHost() = runTest {
        val cookiesSeen = mutableListOf<String?>()
        val client = mockHttpClient { request ->
            cookiesSeen.add(request.headers[HttpHeaders.Cookie])
            when (request.url.encodedPath) {
                "/start" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(
                        HttpHeaders.SetCookie to listOf("session=abc; Path=/; HttpOnly"),
                        HttpHeaders.Location to listOf("https://example.com/next"),
                    ),
                )

                else -> respond("", HttpStatusCode.OK)
            }
        }

        RedirectFollower(client).follow("https://example.com/start", BACKEND_DOMAIN)

        assertNull(cookiesSeen[0])
        assertEquals("session=abc", cookiesSeen[1])
    }

    @Test
    fun follow_should_AccumulateAndOverwriteCookies_when_ChainedRedirects() = runTest {
        var lastCookie: String? = null
        val client = mockHttpClient { request ->
            lastCookie = request.headers[HttpHeaders.Cookie]
            when (request.url.encodedPath) {
                "/a" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(
                        HttpHeaders.SetCookie to listOf("c1=1;", "c2=old"),
                        HttpHeaders.Location to listOf("https://example.com/b"),
                    ),
                )

                "/b" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(
                        HttpHeaders.SetCookie to listOf("c2=2;"),
                        HttpHeaders.Location to listOf("https://example.com/c"),
                    ),
                )

                else -> respond("", HttpStatusCode.OK)
            }
        }

        RedirectFollower(client).follow("https://example.com/a", BACKEND_DOMAIN)

        assertEquals("c1=1; c2=2", lastCookie)
    }

    @Test
    fun follow_should_NotSendCookiesToOtherHost_when_RedirectCrossesHosts() = runTest {
        val cookiesByHost = mutableMapOf<String, String?>()
        val client = mockHttpClient { request ->
            cookiesByHost[request.url.host + request.url.encodedPath] = request.headers[HttpHeaders.Cookie]
            when (request.url.host to request.url.encodedPath) {
                "operator.example" to "/auth" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(
                        HttpHeaders.SetCookie to listOf("operator=secret"),
                        HttpHeaders.Location to listOf("https://api.example/complete"),
                    ),
                )

                "api.example" to "/complete" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(HttpHeaders.Location, "https://operator.example/done"),
                )

                else -> respond("", HttpStatusCode.OK)
            }
        }

        RedirectFollower(client).follow("https://operator.example/auth", BACKEND_DOMAIN)

        assertNull(cookiesByHost["api.example/complete"])
        assertEquals("operator=secret", cookiesByHost["operator.example/done"])
    }

    @Test
    fun follow_should_UseLocationQueryOnly_when_AbsoluteLocationReplacesUrlWithQuery() = runTest {
        val operatorLocation = "https://operator.example/authorize?client_id=operator-client&scope=openid%20verify" +
            "&response_type=code&redirect_uri=https://api.example/authorize-complete&state=operator-state&nonce=n1"
        val requestedUrls = mutableListOf<String>()
        val client = mockHttpClient { request ->
            requestedUrls.add(request.url.toString())
            when (request.url.host) {
                "api.example" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, operatorLocation))
                else -> respond("", HttpStatusCode.OK)
            }
        }

        RedirectFollower(client).follow(
            "https://api.example/authorize?state=app-state&client_id=app-client" +
                "&redirect_url=https%3A%2F%2Fapi.example%2Fcomplete&login_hint=tel:%2B10000000000",
            BACKEND_DOMAIN,
        )

        val operatorUrl = Url(requestedUrls[1])
        assertEquals("operator.example", operatorUrl.host)
        assertEquals("/authorize", operatorUrl.encodedPath)
        assertEquals(listOf("operator-client"), operatorUrl.parameters.getAll("client_id"))
        assertEquals(listOf("operator-state"), operatorUrl.parameters.getAll("state"))
        assertNull(operatorUrl.parameters["login_hint"])
        assertNull(operatorUrl.parameters["redirect_url"])
    }

    @Test
    fun follow_should_DropCurrentQuery_when_RelativeLocationHasNoQuery() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = mockHttpClient { request ->
            requestedUrls.add(request.url.toString())
            when (request.url.encodedPath) {
                "/start" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/next"))
                else -> respond("", HttpStatusCode.OK)
            }
        }

        RedirectFollower(client).follow("https://example.com/start?state=s1#frag", BACKEND_DOMAIN)

        assertEquals("https://example.com/next", requestedUrls[1])
    }

    @Test
    fun follow_should_ResolveLocationPerRfc3986_when_RedirectReferenceGiven() = runTest {
        val base = "https://user:pw@api.example:8443/camara/v0/authorize?state=s1&client_id=c1#frag"
        val expectedByLocation = mapOf(
            "https://operator.example/authorize?state=t1" to "https://operator.example/authorize?state=t1",
            "https://operator.example" to "https://operator.example",
            "//other.example/x?y=1" to "https://other.example/x?y=1",
            "/napi/complete?code=1" to "https://user:pw@api.example:8443/napi/complete?code=1",
            "complete?code=1" to "https://user:pw@api.example:8443/camara/v0/complete?code=1",
            "../done" to "https://user:pw@api.example:8443/camara/done",
            "./x/../y/./z" to "https://user:pw@api.example:8443/camara/v0/y/z",
            "/a/b/../../../c" to "https://user:pw@api.example:8443/c",
            "?code=1" to "https://user:pw@api.example:8443/camara/v0/authorize?code=1",
            "?code=1#f2" to "https://user:pw@api.example:8443/camara/v0/authorize?code=1#f2",
            "#f2" to "https://user:pw@api.example:8443/camara/v0/authorize?state=s1&client_id=c1#f2",
        )

        expectedByLocation.forEach { (location, expectedUrl) ->
            assertEquals(expectedUrl, resolvedRedirect(base, location), "Location: $location")
        }
    }

    private suspend fun resolvedRedirect(startUrl: String, location: String): String {
        val requestedUrls = mutableListOf<String>()
        val client = mockHttpClient { request ->
            requestedUrls.add(request.url.toString())
            if (requestedUrls.size == 1) {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, location))
            } else {
                respond("", HttpStatusCode.OK)
            }
        }

        RedirectFollower(client).follow(startUrl, BACKEND_DOMAIN)

        return requestedUrls[1]
    }

    @Test
    fun follow_should_ResolveRelativeLocation_when_RedirectIsRelative() = runTest {
        val paths = mutableListOf<String>()
        val client = mockHttpClient { request ->
            paths.add(request.url.toString())
            when (request.url.encodedPath) {
                "/camara/authorize" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(HttpHeaders.Location, "/napi/verify/camara-authorize-complete?state=s1&code=c1"),
                )

                else -> respond("", HttpStatusCode.NoContent)
            }
        }

        val result = RedirectFollower(client).follow("https://api.example/camara/authorize", BACKEND_DOMAIN)

        assertEquals(
            "https://api.example/napi/verify/camara-authorize-complete?state=s1&code=c1",
            paths[1],
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun follow_should_ReturnFailureWithResponseDetails_when_ChainEndsWithError() = runTest {
        val client = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/start" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://example.com/end"))
                else -> respond("operator rejected", HttpStatusCode.UnprocessableEntity)
            }
        }

        val result = RedirectFollower(client).follow("https://example.com/start", BACKEND_DOMAIN)

        assertFalse(result.isSuccess)
        assertEquals("Redirect flow finished.\nHTTP status 422 on example.com/end", result.message)
        assertEquals("operator rejected", result.body)
    }

    @Test
    fun follow_should_ReturnSuccess_when_ChainEndsWithErrorOnBackend() = runTest {
        val client = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/authorize" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(HttpHeaders.Location, "https://backend.example.com/napi/verify/camara-authorize-complete?error=access_denied"),
                )

                else -> respond("""{"errorDescription":"denied"}""", HttpStatusCode.BadRequest)
            }
        }

        val result = RedirectFollower(client).follow("https://operator.example/authorize", BACKEND_DOMAIN)

        assertTrue(result.isSuccess)
        assertEquals(
            "Redirect flow finished.\nHTTP status 400 on backend.example.com/napi/verify/camara-authorize-complete",
            result.message,
        )
    }

    @Test
    fun follow_should_ReturnFailure_when_ChainEndsWithErrorOnLookalikeHost() = runTest {
        val client = mockHttpClient {
            respond("", HttpStatusCode.BadRequest)
        }

        val result = RedirectFollower(client).follow("https://backend.example.com.evil.example/napi/verify", BACKEND_DOMAIN)

        assertFalse(result.isSuccess)
    }

    @Test
    fun follow_should_ReturnFailure_when_ChainEndsWithErrorOnBackendPathOfOtherHost() = runTest {
        val client = mockHttpClient {
            respond("", HttpStatusCode.BadRequest)
        }

        val result = RedirectFollower(client).follow("https://api.example.com/napi/verify/camara-authorize-complete", BACKEND_DOMAIN)

        assertFalse(result.isSuccess)
    }

    @Test
    fun follow_should_StopAtMaxRedirects_when_ChainIsInfinite() = runTest {
        var requestCount = 0
        val client = mockHttpClient {
            requestCount++
            respond(
                "",
                HttpStatusCode.Found,
                headersOf(HttpHeaders.Location, "https://example.com/loop"),
            )
        }

        val result = RedirectFollower(client).follow("https://example.com/loop", BACKEND_DOMAIN)

        assertEquals(AppConfig.MAX_REDIRECTS, requestCount)
        assertFalse(result.isSuccess)
        assertEquals("The flow reached max of ${AppConfig.MAX_REDIRECTS} redirects", result.message)
    }

    @Test
    fun follow_should_StopWithFailure_when_RedirectHasNoLocationHeader() = runTest {
        var requestCount = 0
        val client = mockHttpClient {
            requestCount++
            respond("", HttpStatusCode.Found)
        }

        val result = RedirectFollower(client).follow("https://example.com/start", BACKEND_DOMAIN)

        assertEquals(1, requestCount)
        assertFalse(result.isSuccess)
        assertEquals(
            "Received a redirect response without Location header.\nHTTP status 302 on example.com/start",
            result.message,
        )
    }

    @Test
    fun follow_should_IgnoreCookieWithoutEquals_when_SetCookieMalformed() = runTest {
        var secondRequestCookie: String? = "unset"
        var requestCount = 0
        val client = mockHttpClient { request ->
            requestCount++
            if (requestCount == 2) secondRequestCookie = request.headers[HttpHeaders.Cookie]
            when (request.url.encodedPath) {
                "/start" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(
                        HttpHeaders.SetCookie to listOf("malformed"),
                        HttpHeaders.Location to listOf("https://example.com/next"),
                    ),
                )

                else -> respond("", HttpStatusCode.OK)
            }
        }

        RedirectFollower(client).follow("https://example.com/start", BACKEND_DOMAIN)

        assertNull(secondRequestCookie)
    }

    @Test
    fun follow_should_SendDomainCookieToSubdomain_when_DomainAttributeSet() = runTest {
        var authCookie: String? = null
        val client = mockHttpClient { request ->
            when (request.url.host) {
                "operator.example" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(
                        HttpHeaders.SetCookie to listOf("sid=1; Domain=.operator.example; Path=/"),
                        HttpHeaders.Location to listOf("https://auth.operator.example/step"),
                    ),
                )

                else -> {
                    authCookie = request.headers[HttpHeaders.Cookie]
                    respond("", HttpStatusCode.OK)
                }
            }
        }

        RedirectFollower(client).follow("https://operator.example/start", BACKEND_DOMAIN)

        assertEquals("sid=1", authCookie)
    }

    @Test
    fun follow_should_RejectCookie_when_DomainAttributeIsForeign() = runTest {
        var otherHostCookie: String? = "unset"
        val client = mockHttpClient { request ->
            when (request.url.host) {
                "operator.example" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(
                        HttpHeaders.SetCookie to listOf("sid=1; Domain=victim.example"),
                        HttpHeaders.Location to listOf("https://victim.example/next"),
                    ),
                )

                else -> {
                    otherHostCookie = request.headers[HttpHeaders.Cookie]
                    respond("", HttpStatusCode.OK)
                }
            }
        }

        RedirectFollower(client).follow("https://operator.example/start", BACKEND_DOMAIN)

        assertNull(otherHostCookie)
    }

    @Test
    fun follow_should_ReturnSuccess_when_ChainEndsWithErrorOnSelectedLocalBackend() = runTest {
        val client = mockHttpClient { request ->
            when (request.url.host) {
                "operator.example" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(HttpHeaders.Location, "http://10.0.2.2:9094/napi/verify/camara-authorize-complete?error=denied"),
                )

                else -> respond("", HttpStatusCode.BadRequest)
            }
        }

        val result = RedirectFollower(client).follow("https://operator.example/authorize", "http://10.0.2.2:9094")

        assertTrue(result.isSuccess)
    }

    @Test
    fun follow_should_ReturnFailure_when_ChainEndsWithErrorOnOtherPortOfSelectedBackendHost() = runTest {
        val client = mockHttpClient { respond("", HttpStatusCode.BadRequest) }

        val result = RedirectFollower(client).follow("http://10.0.2.2:8080/napi/verify", "http://10.0.2.2:9094")

        assertFalse(result.isSuccess)
    }
}
