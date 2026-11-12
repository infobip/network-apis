package com.infobip.napi.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "infobip")
data class InfobipProperties(
    @field:NotBlank
    val baseUrl: String,
    @field:NotBlank
    val authorizationHeaderValue: String,
    val connectTimeout: Duration,
    val readTimeout: Duration
)
