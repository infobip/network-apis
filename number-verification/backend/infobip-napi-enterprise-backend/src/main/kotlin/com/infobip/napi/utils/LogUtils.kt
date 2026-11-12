package com.infobip.napi.utils

import com.infobip.napi.client.models.InfobipDcqlCredential

private const val LOG_KEEP_CHARS = 10
private const val SD_JWT_SEPARATOR = "~"

object LogUtils {

    fun abbreviate(value: String?): String? =
        value?.let {
            if (it.length <= 2 * LOG_KEEP_CHARS) it else "${it.take(LOG_KEEP_CHARS)}...${it.takeLast(LOG_KEEP_CHARS)}"
        }

    fun abbreviateVpToken(vpToken: String?): String? =
        vpToken?.split(SD_JWT_SEPARATOR)?.joinToString(SD_JWT_SEPARATOR) { abbreviate(it).orEmpty() }

    fun credentialsForLog(credentials: Map<String, InfobipDcqlCredential>?): String? =
        credentials?.entries?.joinToString(", ", "[", "]") { (carrierId, credential) ->
            "$carrierId: (id=${credential.id}, format=${credential.format}, " +
                "credentialAuthorizationJwt=${abbreviate(credential.meta?.credentialAuthorizationJwt)}, " +
                "vctValues=${credential.meta?.vctValues}, error=${credential.code})"
        }

    fun logPrefixWithEnterpriseBeRequestId(enterpriseBeRequestId: String) =
        logCorrelationPrefix(enterpriseBeRequestId, null)

    fun logCorrelationPrefix(enterpriseBeRequestId: String?, miRequestId: String?): String =
        listOfNotNull(
            enterpriseBeRequestId?.takeIf { it.isNotBlank() }?.let { "enterpriseBeRequestId=$it," },
            miRequestId?.takeIf { it.isNotBlank() }?.let { "miRequestId=$it," }
        ).joinToString(" ")
}
