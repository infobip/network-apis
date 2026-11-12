package com.infobip.napi.service.simbased

import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import java.util.Base64

data class VpTokenClaims(val nonce: String?, val state: String?)

private val log = LoggerFactory.getLogger(VpTokenClaims::class.java)

private const val SD_JWT_SEPARATOR = "~"

fun parseVpTokenClaims(vpToken: String, jsonMapper: JsonMapper): VpTokenClaims? {
    val keyBindingJwt = vpToken.split(SD_JWT_SEPARATOR).takeIf { it.size > 1 }?.last()
    val payload = keyBindingJwt?.split(".")?.getOrNull(1)
    if (payload == null) {
        log.warn("Could not parse vpToken claims: no key-binding JWT payload after the last '{}'", SD_JWT_SEPARATOR)
        return null
    }
    return try {
        val claims = jsonMapper.readTree(Base64.getUrlDecoder().decode(payload))
        VpTokenClaims(
            claims.path("nonce").takeIf { node -> node.isString }?.asString(),
            claims.path("state").takeIf { node -> node.isString }?.asString()
        )
    } catch (e: Exception) {
        log.warn("Could not parse vpToken claims: {}: {}", e.javaClass.simpleName, e.message)
        null
    }
}
