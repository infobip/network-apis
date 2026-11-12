package com.infobip.napi.di

import io.ktor.client.HttpClient
import com.infobip.napi.network.ApiClient
import com.infobip.napi.network.AuthorizationProvider
import com.infobip.napi.network.DefaultAuthorizationProvider
import com.infobip.napi.network.ClientCredentialsClient
import com.infobip.napi.network.RedirectFollower
import com.infobip.napi.network.createHttpClient
import com.infobip.napi.repository.DefaultSimBasedRepository
import com.infobip.napi.repository.DefaultVerificationRepository
import com.infobip.napi.repository.SimBasedRepository
import com.infobip.napi.repository.VerificationRepository
import com.infobip.napi.settings.SettingsRepository
import org.koin.dsl.module

val appModule = module {
    single<HttpClient> { createHttpClient() }
    single { ApiClient(get()) }
    single { ClientCredentialsClient(get()) }
    single<AuthorizationProvider> { DefaultAuthorizationProvider(get(), get()) }
    single { RedirectFollower(get()) }
    single<VerificationRepository> { DefaultVerificationRepository(get(), get(), get()) }
    single<SimBasedRepository> { DefaultSimBasedRepository(get(), get()) }
    single { SettingsRepository(get()) }
}
