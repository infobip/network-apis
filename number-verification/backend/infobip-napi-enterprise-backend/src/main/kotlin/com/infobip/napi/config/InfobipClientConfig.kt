package com.infobip.napi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

@Configuration
class InfobipClientConfig {

    @Bean
    fun infobipRestClient(builder: RestClient.Builder, properties: InfobipProperties): RestClient =
        builder
            .baseUrl(properties.baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, properties.authorizationHeaderValue)
            .requestFactory(
                JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build()
                ).apply { setReadTimeout(properties.readTimeout) }
            )
            .build()
}
