package com.infobip.napi.service

class RequestRejectedException(
    val description: String,
    val token: String? = null,
) : RuntimeException(description)
