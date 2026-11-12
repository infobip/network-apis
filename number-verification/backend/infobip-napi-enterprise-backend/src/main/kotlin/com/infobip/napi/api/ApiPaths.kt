package com.infobip.napi.api

object ApiPaths {
    const val API_PATH_PREFIX = "/napi"

    const val VERIFY_PREFIX = "$API_PATH_PREFIX/verify"
    const val VERIFY = "/{verificationType}/{msisdn}"
    const val RESULT = "/result/{token}"
    const val CALLBACK = "/callback"
    const val AUTHORIZE_COMPLETE = "/camara-authorize-complete"

    const val SIM_BASED_PREFIX = "$API_PATH_PREFIX/sim-based"
    const val VERIFY_PHONE_NUMBER_DCQL = "/verify/dcql"
    const val VERIFY_PHONE_NUMBER = "/verify"
    const val GET_PHONE_NUMBER_DCQL = "/device-phone-number/dcql"
    const val GET_PHONE_NUMBER = "/device-phone-number"

    const val DEVICE_ADDRESS = "/device-address"

    const val OAUTH2_TOKEN = "/oauth2/token"

    const val MSISDN_PATTERN = "^\\+?[1-9][0-9]{4,14}$"
}
