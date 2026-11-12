package com.infobip.napi.api

import com.infobip.napi.config.VerifyProperties
import com.infobip.napi.utils.IpAddress
import com.infobip.napi.utils.resolveIpAddress
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(ApiPaths.API_PATH_PREFIX)
class InfoController(
    private val verifyProperties: VerifyProperties
) {
    @GetMapping(ApiPaths.DEVICE_ADDRESS)
    fun deviceAddress(request: HttpServletRequest): ResponseEntity<IpAddress> =
        ResponseEntity.ok(resolveIpAddress(request, verifyProperties.usingProxy))
}
