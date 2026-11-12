package com.infobip.napi.model.domain

enum class SimCredentialError(val description: String) {
    NO_SIM("No supported SIM found."),
    CANCELLED("SIM-based verification was cancelled."),
    NO_CREDENTIAL("The SIM did not provide a credential."),
    UNSUPPORTED("SIM-based verification is not supported on this device."),
    NO_ACTIVITY("SIM-based verification needs the app to be in the foreground."),
    ERROR("Unable to get a credential from the SIM."),
}
