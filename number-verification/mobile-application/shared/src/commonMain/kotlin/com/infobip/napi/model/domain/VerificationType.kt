package com.infobip.napi.model.domain

/** Verification type in the backend verify path `/verify/{pathSegment}/{msisdn}`. */
enum class VerificationType(val pathSegment: String) {
    SMV(pathSegment = "smv"),
    CAMARA(pathSegment = "nv"),
}
