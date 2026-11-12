package com.infobip.napi.service.auth

import com.infobip.napi.api.models.auth.OAuthErrorResponse
import com.infobip.napi.config.AuthProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.time.Clock
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val log = LoggerFactory.getLogger(AccessTokenService::class.java)

private const val HMAC_ALGORITHM = "HmacSHA256"
private const val JWT_PARTS = 3
private const val TOKEN_TYPE_BEARER = "Bearer"

/**
 * THIS IS AN EXAMPLE OF A BASIC AUTHENTICATION SERVICE, THE IMPLEMENTATION OF THE AUTHORIZATION LOGIC SHOULD BE ADAPTED TO YOUR SPECIFIC USE CASE.
 *
 * Service that issues and validates OAuth2 access tokens.
 * Supports both Bearer and Basic authentication schemes.
 */
@Service
class AccessTokenService(
    private val authProperties: AuthProperties, private val jsonMapper: JsonMapper, private val clock: Clock = Clock.systemUTC()
) : AccessToken {

    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val signingKey = SecretKeySpec(authProperties.tokenSecret.toByteArray(), HMAC_ALGORITHM)
    private val encodedHeader = encoder.encodeToString(jsonMapper.writeValueAsBytes(mapOf("alg" to "HS256", "typ" to "JWT")))

    override fun getOauthToken(clientId: String?, clientSecret: String?): OAuthTokenResult {

        if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
            return OAuthTokenResult(
                error = OAuthErrorResponse("invalid_request", "client_id and client_secret are required")
            )
        }
        if (!isValidClient(clientId, clientSecret)) {
            log.debug("Rejecting token request for unknown client: {}", clientId)
            return OAuthTokenResult(
                error = OAuthErrorResponse("invalid_client", "Client authentication failed")
            )
        }

        return OAuthTokenResult(issueOAuth2Token(clientId))
    }

    override fun isValidBearer(token: String): Boolean {
        val parts = token.split(".")
        return parts.size == JWT_PARTS && try {
            val signatureMatches = MessageDigest.isEqual(sign("${parts[0]}.${parts[1]}"), decoder.decode(parts[2]))
            signatureMatches && isNotExpired(parts[1])
        } catch (e: Exception) {
            log.warn("Could not validate access token: {}: {}", e.javaClass.simpleName, e.message)
            false
        }
    }

    override fun isValidBasic(credentials: String): Boolean {
        val decoded = try {
            String(Base64.getDecoder().decode(credentials))
        } catch (_: IllegalArgumentException) {
            return false
        }
        val username = decoded.substringBefore(':', "")
        val password = decoded.substringAfter(':', "")
        return username.isNotEmpty() && password.isNotEmpty() && isValidClient(username, password)
    }

    private fun isValidClient(clientId: String, clientSecret: String): Boolean =
        authProperties.clients.any {
            MessageDigest.isEqual(clientId.toByteArray(), it.clientId.toByteArray()) &&
                MessageDigest.isEqual(clientSecret.toByteArray(), it.clientSecret.toByteArray())
        }

    private fun issueOAuth2Token(subject: String): IssuedAccessToken {
        val issuedAt = clock.instant().epochSecond
        val expiresIn = authProperties.tokenTtl.toSeconds()
        val claims = mapOf("sub" to subject, "iat" to issuedAt, "exp" to issuedAt + expiresIn)
        val signingInput = "$encodedHeader.${encoder.encodeToString(jsonMapper.writeValueAsBytes(claims))}"
        return IssuedAccessToken("$signingInput.${encoder.encodeToString(sign(signingInput))}", TOKEN_TYPE_BEARER, expiresIn)
    }

    private fun isNotExpired(encodedClaims: String): Boolean {
        val expiresAt = jsonMapper.readTree(decoder.decode(encodedClaims)).path("exp")
        return expiresAt.isNumber && expiresAt.asLong() > clock.instant().epochSecond
    }

    private fun sign(signingInput: String): ByteArray =
        Mac.getInstance(HMAC_ALGORITHM).apply { init(signingKey) }.doFinal(signingInput.toByteArray())
}
