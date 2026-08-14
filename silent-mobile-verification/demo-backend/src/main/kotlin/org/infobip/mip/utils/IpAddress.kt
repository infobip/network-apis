package org.infobip.mip.utils

import jakarta.servlet.http.HttpServletRequest
import java.util.regex.Pattern

private val forwardedForPattern: Pattern = Pattern.compile("^[^, ]+")
data class IpAddress(val address: String, val port: Int)

fun resolveIpAddress(request: HttpServletRequest, usingProxy: Boolean): IpAddress {
    return if (usingProxy) {
        resolveIpAddress(request.getHeader("X-Forwarded-For")!!)
    } else {
        IpAddress(request.remoteAddr, request.remotePort)
    }
}

fun resolveIpAddress(forwardedForAddress: String): IpAddress {
    val (ip, port) = extractIpAndPort(forwardedForAddress)
    return IpAddress(ip, port)
}

/**
 * Extract IP and port from address string. Handles both IPv4 and IPv6.
 * Examples:
 * - 1.2.3.4:5678
 * - [2001:db8::1]:5678
 * - 1.2.3.4
 * - [2001:db8::1]
 */
private fun extractIpAndPort(addressString: String): Pair<String, Int> {
    val addressRaw = forwardedForPattern.matcher(addressString).run {
        if (find()) group() else addressString
    }

    return if (addressRaw.startsWith("[")) {
        val endBracketIdx = addressRaw.indexOf(']')
        if (endBracketIdx == -1) {
            throw IllegalArgumentException("Invalid IPv6 address format: $addressRaw")
        }
        val ip = addressRaw.substring(1, endBracketIdx)
        val port = if (endBracketIdx + 1 < addressRaw.length && addressRaw[endBracketIdx + 1] == ':') {
            addressRaw.substring(endBracketIdx + 2).toIntOrNull() ?: 0
        } else {
            0
        }
        Pair(ip, port)
    } else {
        val parts = addressRaw.split(':')
        if (parts.size == 2) {
            Pair(parts[0], parts[1].toIntOrNull() ?: 0)
        } else {
            Pair(addressRaw, 0)
        }
    }
}