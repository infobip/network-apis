package com.infobip.napi.service.auth

import com.infobip.napi.api.models.auth.OAuthErrorResponse

data class IssuedAccessToken(
    val accessToken: String, val accessTokenType: String, val expiresInSeconds: Long
)

data class OAuthTokenResult(
    val success: Boolean, val issuedAccessToken: IssuedAccessToken? = null, val error: OAuthErrorResponse? = null
) {
    constructor(issuedAccessToken: IssuedAccessToken) : this(
        success = true, issuedAccessToken = issuedAccessToken
    )

    constructor(error: OAuthErrorResponse) : this(
        success = false, error = error
    )
}

/**
 * THIS IS AN EXAMPLE OF A BASIC AUTHENTICATION SERVICE, THE IMPLEMENTATION OF THE AUTHORIZATION LOGIC SHOULD BE ADAPTED TO YOUR SPECIFIC USE CASE.
 *
 * Service that issues and validates OAuth2 access tokens.
 * Supports both Bearer and Basic authentication schemes.
 */
interface AccessToken {

    fun getOauthToken(clientId: String?, clientSecret: String?): OAuthTokenResult

    fun isValidBearer(token: String): Boolean

    fun isValidBasic(credentials: String): Boolean

}
