package com.infobip.napi.config

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "auth")
data class AuthProperties(
    @field:NotBlank
    val tokenSecret: String,
    val tokenTtl: Duration,
    @field:NotEmpty
    @field:Valid
    val clients: Set<ClientCredentials>
) {
    data class ClientCredentials(
        @field:NotBlank
        val clientId: String,
        @field:NotBlank
        val clientSecret: String
    )
}
