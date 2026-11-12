package com.infobip.napi.di

import com.infobip.napi.network.ClientCredentialsClient
import com.infobip.napi.ui.phonenumber.PhoneNumberViewModel
import com.infobip.napi.ui.settings.SettingsViewModel
import com.infobip.napi.ui.verify.VerificationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { SettingsViewModel(get(), get<ClientCredentialsClient>()) }
    viewModel { VerificationViewModel(get(), get(), get()) }
    viewModel { PhoneNumberViewModel(get(), get()) }
}
