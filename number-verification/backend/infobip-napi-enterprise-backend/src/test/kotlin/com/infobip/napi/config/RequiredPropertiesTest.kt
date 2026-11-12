package com.infobip.napi.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.validation.BindValidationException
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

private val REQUIRED_VARIABLES = mapOf(
    "ENTERPRISE_BACKEND_DOMAIN" to "https://backend.example.com",
    "ENTERPRISE_AUTH_TOKEN_SECRET" to "token-secret",
    "ENTERPRISE_CLIENT_ID" to "client",
    "ENTERPRISE_CLIENT_SECRET" to "client-secret",
    "INFOBIP_BASE_URL" to "https://api.example.com",
    "INFOBIP_AUTHORIZATION_HEADER_VALUE" to "IBSSO token"
)

class RequiredPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withUserConfiguration(PropertiesConfiguration::class.java)

    @Test
    fun startup_should_Succeed_when_AllRequiredVariablesAreSet() {
        contextRunner.withPropertyValues(*propertyValues(REQUIRED_VARIABLES))
            .run { assertThat(it.startupFailure).isNull() }
    }

    @ParameterizedTest
    @CsvSource(
        "ENTERPRISE_BACKEND_DOMAIN, domain",
        "ENTERPRISE_AUTH_TOKEN_SECRET, tokenSecret",
        "ENTERPRISE_CLIENT_ID, clientId",
        "ENTERPRISE_CLIENT_SECRET, clientSecret",
        "INFOBIP_BASE_URL, baseUrl",
        "INFOBIP_AUTHORIZATION_HEADER_VALUE, authorizationHeaderValue"
    )
    fun startup_should_FailOnMissingField_when_RequiredVariableIsNotSet(variable: String, field: String) {
        contextRunner.withPropertyValues(*propertyValues(REQUIRED_VARIABLES - variable))
            .run {
                assertThat(it.startupFailure).rootCause()
                    .isInstanceOf(BindValidationException::class.java)
                    .hasMessageContaining(field)
            }
    }

    private fun propertyValues(variables: Map<String, String>) = variables.map { "${it.key}=${it.value}" }.toTypedArray()

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(VerifyProperties::class, AuthProperties::class, InfobipProperties::class)
    class PropertiesConfiguration
}
