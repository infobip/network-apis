package com.infobip.napi

import androidx.compose.ui.window.ComposeUIViewController
import com.infobip.napi.di.initKoin

fun MainViewController() = ComposeUIViewController {
    initKoin()
    App()
}
