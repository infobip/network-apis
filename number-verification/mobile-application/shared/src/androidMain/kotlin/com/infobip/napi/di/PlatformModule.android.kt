package com.infobip.napi.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import com.infobip.napi.network.AndroidCellularController
import com.infobip.napi.network.AndroidSimCredentialController
import com.infobip.napi.network.CellularController
import com.infobip.napi.network.SimCredentialController
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<Context> { AndroidContext.applicationContext }
    single<CellularController> { AndroidCellularController(get()) }
    single<SimCredentialController> { AndroidSimCredentialController(get()) }
    single<Settings> {
        val prefs = get<Context>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        SharedPreferencesSettings(prefs)
    }
}

private const val PREFS_NAME = "napi_client_settings"
