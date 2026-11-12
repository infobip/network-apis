package com.infobip.napi.api.auth

import com.infobip.napi.api.models.auth.OAuthErrorResponse
import com.infobip.napi.service.auth.AccessToken
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import tools.jackson.databind.json.JsonMapper

private val log = LoggerFactory.getLogger(AuthorizationInterceptor::class.java)

private const val BEARER_SCHEME = "Bearer"
private const val BASIC_SCHEME = "Basic"

/**
 * THIS IS AN EXAMPLE OF A BASIC AUTHENTICATION INTERCEPTOR, THE IMPLEMENTATION OF THE AUTHORIZATION LOGIC SHOULD BE ADAPTED TO YOUR SPECIFIC USE CASE.
 *
 * Interceptor that checks for the presence of a valid Authorization header in incoming requests.
 * Supports both Bearer and Basic authentication schemes.
 */
@Component
class AuthorizationInterceptor(
    private val accessTokenService: AccessToken,
    private val jsonMapper: JsonMapper
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (isAuthorized(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            return true
        }
        log.debug("Rejecting unauthorized request to {}", request.requestURI)
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_SCHEME)
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        jsonMapper.writeValue(
            response.outputStream,
            OAuthErrorResponse("invalid_token", "Missing or invalid Authorization header")
        )
        return false
    }

    private fun isAuthorized(authorization: String?): Boolean {
        val scheme = authorization?.substringBefore(' ')
        val credentials = authorization?.substringAfter(' ', "")?.trim().orEmpty()
        return when {
            credentials.isEmpty() -> false
            scheme.equals(BEARER_SCHEME, ignoreCase = true) -> accessTokenService.isValidBearer(credentials)
            scheme.equals(BASIC_SCHEME, ignoreCase = true) -> accessTokenService.isValidBasic(credentials)
            else -> false
        }
    }
}
