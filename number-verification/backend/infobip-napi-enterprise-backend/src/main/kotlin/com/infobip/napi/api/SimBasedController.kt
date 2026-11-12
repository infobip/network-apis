package com.infobip.napi.api

import com.infobip.napi.api.models.numberverify.simbased.DcqlRequest
import com.infobip.napi.api.models.numberverify.simbased.DcqlResponse
import com.infobip.napi.api.models.numberverify.simbased.GetPhoneNumberRequest
import com.infobip.napi.api.models.numberverify.simbased.GetPhoneNumberResponse
import com.infobip.napi.api.models.numberverify.simbased.VerifyPhoneNumberRequest
import com.infobip.napi.api.models.numberverify.simbased.VerifyPhoneNumberResponse
import com.infobip.napi.service.simbased.SimBasedService
import jakarta.validation.Valid
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(ApiPaths.SIM_BASED_PREFIX)
class SimBasedController(
    private val simBasedService: SimBasedService
) {

    @PostMapping(ApiPaths.VERIFY_PHONE_NUMBER_DCQL, consumes = [APPLICATION_JSON_VALUE])
    fun verifyPhoneNumberDcql(
        @Valid @RequestBody request: DcqlRequest
    ): ResponseEntity<DcqlResponse> {
        return ResponseEntity.ok(simBasedService.verifyPhoneNumberDcql(request.carrierIds!!))
    }

    @PostMapping(ApiPaths.VERIFY_PHONE_NUMBER, consumes = [APPLICATION_JSON_VALUE])
    fun verifyPhoneNumber(
        @Valid @RequestBody request: VerifyPhoneNumberRequest
    ): ResponseEntity<VerifyPhoneNumberResponse> {
        return ResponseEntity.ok(simBasedService.verifyPhoneNumber(request.vpToken!!, request.phoneNumber!!))
    }

    @PostMapping(ApiPaths.GET_PHONE_NUMBER_DCQL, consumes = [APPLICATION_JSON_VALUE])
    fun getPhoneNumberDcql(
        @Valid @RequestBody request: DcqlRequest
    ): ResponseEntity<DcqlResponse> {
        return ResponseEntity.ok(simBasedService.getPhoneNumberDcql(request.carrierIds!!))
    }

    @PostMapping(ApiPaths.GET_PHONE_NUMBER, consumes = [APPLICATION_JSON_VALUE])
    fun getPhoneNumber(
        @Valid @RequestBody request: GetPhoneNumberRequest
    ): ResponseEntity<GetPhoneNumberResponse> {
        return ResponseEntity.ok(simBasedService.getPhoneNumber(request.vpToken!!))
    }
}
