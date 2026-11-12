package com.infobip.napi

import com.infobip.napi.config.AuthProperties
import com.infobip.napi.config.InfobipProperties
import com.infobip.napi.config.VerifyProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(VerifyProperties::class, AuthProperties::class, InfobipProperties::class)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
