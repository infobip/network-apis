package com.infobip.napi.client

import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse
import org.springframework.web.client.RestClientException

class InfobipCallException(val status: HttpStatusCode) : RuntimeException("Infobip API call failed with status $status")

internal inline fun <reified T : Any> typeReference(): ParameterizedTypeReference<T> = object : ParameterizedTypeReference<T>() {}

internal fun <T : Any> ConvertibleClientHttpResponse.bodyOrNull(bodyType: ParameterizedTypeReference<T>): T? =
    try {
        bodyTo(bodyType)
    } catch (_: RestClientException) {
        null
    }
