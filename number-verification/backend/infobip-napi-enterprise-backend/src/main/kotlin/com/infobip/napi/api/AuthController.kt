package com.infobip.napi.api

import com.infobip.napi.api.models.auth.AccessTokenResponse
import com.infobip.napi.api.models.auth.OAuthErrorResponse
import com.infobip.napi.service.auth.AccessToken
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val log = LoggerFactory.getLogger(AuthController::class.java)

private const val GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials"
private const val INVALID_REQUEST = "invalid_request"

/**
 * THIS IS AN EXAMPLE OF A BASIC AUTHENTICATION CONTROLLER, THE IMPLEMENTATION OF THE AUTHORIZATION LOGIC SHOULD BE ADAPTED TO YOUR SPECIFIC USE CASE.
 *
 * Controller that handles OAuth2 token requests.
 * Issues Bearer access tokens for the client credentials grant.
 */
@RestController
@RequestMapping(ApiPaths.API_PATH_PREFIX)
class AuthController(
    private val accessTokenService: AccessToken
) {

    @PostMapping(ApiPaths.OAUTH2_TOKEN, consumes = [APPLICATION_FORM_URLENCODED_VALUE])
    fun token(
        @RequestParam(name = "grant_type", required = false) grantType: String?,
        @RequestParam(name = "client_id", required = false) clientId: String?,
        @RequestParam(name = "client_secret", required = false) clientSecret: String?
    ): ResponseEntity<*> {
        log.debug("Token request: grantType: {}, clientId: {}", grantType, clientId)

        if (grantType != GRANT_TYPE_CLIENT_CREDENTIALS) {
            return ResponseEntity.badRequest()
                .body(OAuthErrorResponse("unsupported_grant_type", "Only $GRANT_TYPE_CLIENT_CREDENTIALS grant type is supported"))
        }

        val accessTokenResponse = accessTokenService.getOauthToken(clientId, clientSecret)
        if (!accessTokenResponse.success || accessTokenResponse.issuedAccessToken?.accessToken == null) {
            val error = accessTokenResponse.error ?: OAuthErrorResponse("invalid_client", "Client authentication failed")
            val status = if (error.error == INVALID_REQUEST) HttpStatus.BAD_REQUEST else HttpStatus.UNAUTHORIZED
            return ResponseEntity.status(status).body(error)
        }

        return ResponseEntity.ok(
            AccessTokenResponse(
                accessTokenResponse.issuedAccessToken.accessToken,
                accessTokenResponse.issuedAccessToken.accessTokenType,
                accessTokenResponse.issuedAccessToken.expiresInSeconds
            )
        )
    }
}
