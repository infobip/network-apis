package com.infobip.napi

import android.app.Application
import com.infobip.napi.di.AndroidContext
import com.infobip.napi.di.initKoin

class NapiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidContext.init(this)
        initKoin()
    }
}
