package com.infobip.napi.utils

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(IpAddress::class.java)

private const val FORWARDED_FOR_HEADER = "X-Forwarded-For"
private const val UNKNOWN_PORT = 0

data class IpAddress(val address: String, val port: Int)

fun resolveIpAddress(request: HttpServletRequest, usingProxy: Boolean): IpAddress {
    // If a reverse proxy is being used, the remoteAddr in the request object is the IP address of the proxy and not of the device
    // In that case the proxy puts the IP address of the device in the X-Forwarded-For header, so we need to check that header
    if (usingProxy) {
        val forwardedFor = request.getHeader(FORWARDED_FOR_HEADER)
        if (!forwardedFor.isNullOrBlank()) {
            try {
                return resolveIpAddress(forwardedFor)
            } catch (_: IllegalArgumentException) {
                log.warn("{} header is malformed while proxy mode is enabled, falling back to remote address", FORWARDED_FOR_HEADER)
            }
        } else {
            log.warn("{} header is missing while proxy mode is enabled, falling back to remote address", FORWARDED_FOR_HEADER)
        }
    }
    return IpAddress(request.remoteAddr, request.remotePort)
}

internal fun resolveIpAddress(forwardedForAddress: String): IpAddress {
    // X-Forwarded-For header can contain multiple IP addresses separated by commas
    // The IP address of the device that made the request is the last in the chain as it is the one that connected to the proxy
    val addressRaw = forwardedForAddress.split(',').last().trim()
    require(addressRaw.isNotEmpty()) { "Empty address in $FORWARDED_FOR_HEADER: $forwardedForAddress" }

    // If address starts with "[" it is an IPv6 address, and the port is after the closing "]" bracket
    if (addressRaw.startsWith("[")) {
        val endBracketIndex = addressRaw.indexOf(']')
        require(endBracketIndex != -1) { "Invalid IPv6 address format: $addressRaw" }
        val port = addressRaw.substring(endBracketIndex + 1).removePrefix(":").toPortOrUnknown()
        return IpAddress(addressRaw.substring(1, endBracketIndex), port)
    }

    val parts = addressRaw.split(':')
    return if (parts.size == 2) {
        IpAddress(parts[0], parts[1].toPortOrUnknown())
    } else {
        IpAddress(addressRaw, UNKNOWN_PORT)
    }
}

private fun String.toPortOrUnknown(): Int = toIntOrNull()?.takeIf { it in 0..65535 } ?: UNKNOWN_PORT
