package com.infobip.napi.config

import com.infobip.napi.api.ApiPaths
import com.infobip.napi.api.auth.AuthorizationInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val authorizationInterceptor: AuthorizationInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authorizationInterceptor)
            .addPathPatterns("${ApiPaths.API_PATH_PREFIX}/**")
            .excludePathPatterns(*PUBLIC_PATHS)
    }

    companion object {
        val PUBLIC_PATHS = arrayOf(
            "${ApiPaths.API_PATH_PREFIX}${ApiPaths.OAUTH2_TOKEN}",
            "${ApiPaths.VERIFY_PREFIX}${ApiPaths.CALLBACK}",
            "${ApiPaths.VERIFY_PREFIX}${ApiPaths.AUTHORIZE_COMPLETE}"
        )
    }
}
