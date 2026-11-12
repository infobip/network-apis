package com.infobip.napi.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "verify")
data class VerifyProperties(
    @field:NotBlank
    val domain: String,
    val usingProxy: Boolean,
    val resultTtl: Duration,
    val authorizationStateTtl: Duration,
    val maxEntries: Long,
    val camaraClientId: String? = null
)
