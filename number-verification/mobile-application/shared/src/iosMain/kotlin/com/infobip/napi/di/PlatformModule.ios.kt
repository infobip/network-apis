package com.infobip.napi.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import com.infobip.napi.network.CellularController
import com.infobip.napi.network.ManualCellularController
import com.infobip.napi.network.SimCredentialController
import com.infobip.napi.network.UnsupportedSimCredentialController
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual fun platformModule(): Module = module {
    single<CellularController> { ManualCellularController() }
    single<SimCredentialController> { UnsupportedSimCredentialController() }
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
}
