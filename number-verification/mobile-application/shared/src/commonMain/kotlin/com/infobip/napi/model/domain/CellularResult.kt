package com.infobip.napi.model.domain

enum class CellularResult(val description: String) {
    SUCCESS(""),
    CELLULAR_UNAVAILABLE("Mobile Internet needs to be turned on."),
    MANUAL_REQUIRED("Automatic switch to cellular is not possible on this platform. Connect to mobile data manually."),
    FAIL_NETWORK_RESET("Failed to reset to the default network."),
    ERROR("Unable to switch to Mobile Internet."),
}
