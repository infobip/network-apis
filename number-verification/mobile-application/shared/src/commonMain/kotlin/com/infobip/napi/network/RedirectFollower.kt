package com.infobip.napi.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.parseQueryString
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.http.renderCookieHeader
import io.ktor.http.takeFrom
import com.infobip.napi.config.AppConfig

data class RedirectFollowerResult(
    val isSuccess: Boolean,
    val message: String,
    val headers: String? = null,
    val body: String? = null
)

class RedirectFollower(private val client: HttpClient) {

    suspend fun follow(redirectUrl: String, backendDomain: String): RedirectFollowerResult {
        var currentUrl = Url(redirectUrl)
        val cookieJar = RedirectCookieJar()

        repeat(AppConfig.MAX_REDIRECTS) {
            val cookieHeader = cookieJar.cookieHeaderFor(currentUrl)
            val response: HttpResponse = client.get(currentUrl) {
                if (cookieHeader != null) header(HttpHeaders.Cookie, cookieHeader)
            }
            val status = response.status.value

            cookieJar.store(currentUrl, response.headers.getAll(HttpHeaders.SetCookie).orEmpty())

            if (status !in 300..399) {

                // If status is not 2xx but the response is from the backend a success response is returned.
                // Success is returned as the mobile app will check with the backend on the response status, so there is no need to break the flow at this point.
                val isSuccess = status in 200..299 || isTrustedBackend(currentUrl, backendDomain)

                return RedirectFollowerResult(
                    isSuccess,
                    "Redirect flow finished.\nHTTP status $status on ${currentUrl.host}${currentUrl.encodedPath}",
                    response.headers.entries().joinToString("\n"),
                    response.bodyAsText()
                )
            }

            val redirectUrl =
                response.headers[HttpHeaders.Location] ?: return RedirectFollowerResult(
                    false,
                    "Received a redirect response without Location header.\nHTTP status $status on ${currentUrl.host}${currentUrl.encodedPath}",
                    response.headers.entries().joinToString("\n"),
                    response.bodyAsText()
                )

            currentUrl = resolveRedirectUrl(currentUrl, redirectUrl)
        }

        return RedirectFollowerResult(
            false,
            "The flow reached max of ${AppConfig.MAX_REDIRECTS} redirects"
        )
    }

    private fun isTrustedBackend(url: Url, backendDomain: String): Boolean =
        url.encodedPath.startsWith(AppConfig.BACKEND_API_PATH_PREFIX) && url.hasSameHostAndPort(backendDomain)

    private fun Url.hasSameHostAndPort(domain: String): Boolean {
        if (domain.isBlank()) return false
        val backend = runCatching { Url(domain.trim()) }.getOrNull() ?: return false
        return backend.host.equals(host, ignoreCase = true) && backend.port == port
    }

    // Resolves the Location against the current URL per RFC 3986 section 5.2. URLBuilder.takeFrom alone is not used for
    // every case: it appends to the existing query, keeps credentials across hosts and misparses "?..." and "#..." references.
    private fun resolveRedirectUrl(currentUrl: Url, redirectUrl: String): Url {
        val location = redirectUrl.trim()
        val builder = when {
            isAbsolute(location) -> URLBuilder(location)
            location.startsWith("//") -> URLBuilder("${currentUrl.protocol.name}:$location")
            location.startsWith("?") -> URLBuilder(currentUrl).apply {
                parameters.clear()
                encodedParameters.appendAll(
                    parseQueryString(
                        location.substring(1).substringBefore('#'), decode = false
                    )
                )
                encodedFragment = location.substringAfter('#', missingDelimiterValue = "")
            }

            location.startsWith("#") -> URLBuilder(currentUrl).apply {
                encodedFragment = location.substring(1)
            }

            else -> URLBuilder(currentUrl).apply {
                parameters.clear()
                fragment = ""
            }.takeFrom(location)
        }
        builder.encodedPathSegments = removeDotSegments(builder.encodedPathSegments)
        return builder.build()
    }

    // A relative reference cannot contain ':' before the first '/', '?' or '#', so a colon there marks a scheme.
    private fun isAbsolute(location: String): Boolean =
        location.substringBefore('/').substringBefore('?').substringBefore('#').contains(':')

    private fun removeDotSegments(segments: List<String>): List<String> {
        if (segments.none { it == "." || it == ".." }) return segments
        val output = mutableListOf<String>()
        segments.forEach { segment ->
            when (segment) {
                "." -> Unit
                ".." -> if (output.size > 1) output.removeAt(output.lastIndex)
                else -> output.add(segment)
            }
        }
        if (segments.last() == "." || segments.last() == "..") output.add("")
        return output
    }
}

private class RedirectCookieJar {
    private val storage = AcceptAllCookiesStorage()

    suspend fun cookieHeaderFor(url: Url): String? =
        storage.get(url).takeIf { it.isNotEmpty() }?.joinToString("; ", transform = ::renderCookieHeader)

    suspend fun store(url: Url, setCookieHeaders: List<String>) {
        setCookieHeaders
            .filter { it.substringBefore(';').contains('=') }
            .mapNotNull { runCatching { parseServerSetCookieHeader(it) }.getOrNull() }
            .filter { it.name.isNotBlank() && it.domainMatches(url.host) }
            .forEach { storage.addCookie(url, it.withDefaultPath(url)) }
    }

    private fun Cookie.domainMatches(host: String): Boolean {
        val cookieDomain = domain?.trimStart('.')?.lowercase()?.ifEmpty { null } ?: return true
        val requestHost = host.lowercase()
        return requestHost == cookieDomain || requestHost.endsWith(".$cookieDomain")
    }

    private fun Cookie.withDefaultPath(url: Url): Cookie {
        if (path?.startsWith("/") == true) return this
        val requestPath = url.encodedPath
        val defaultPath = if (!requestPath.startsWith("/") || requestPath.count { it == '/' } <= 1) {
            "/"
        } else {
            requestPath.substringBeforeLast('/')
        }
        return copy(path = defaultPath)
    }
}
