package com.infobip.napi.ui.verify

import com.infobip.napi.network.AuthorizationProvider
import com.infobip.napi.settings.SettingsState

class FakeAuthorizationProvider(
    private val header: String? = "Bearer key-1",
    private val failure: Throwable? = null,
) : AuthorizationProvider {

    var callCount = 0

    override suspend fun authorizationHeader(settings: SettingsState): String? {
        callCount++
        failure?.let { throw it }
        return header
    }
}
