package com.infobip.napi.api

import com.infobip.napi.api.models.numberverify.CallbackRequest
import com.infobip.napi.api.models.numberverify.ClientVerifyResponse
import com.infobip.napi.api.models.numberverify.ClientVerifyResultResponse
import com.infobip.napi.config.VerifyProperties
import com.infobip.napi.service.RequestRejectedException
import com.infobip.napi.service.nv.NumberVerifyService
import com.infobip.napi.service.smv.SilentMobileVerificationService
import com.infobip.napi.service.store.VerificationResultStore
import com.infobip.napi.utils.resolveIpAddress
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val log = LoggerFactory.getLogger(VerifyController::class.java)

private const val VERIFICATION_TYPE_NV = "nv"
private const val VERIFICATION_TYPE_SMV = "smv"

@RestController
@RequestMapping(ApiPaths.VERIFY_PREFIX)
class VerifyController(
    private val silentMobileVerificationService: SilentMobileVerificationService,
    private val numberVerifyService: NumberVerifyService,
    private val resultStore: VerificationResultStore,
    private val verifyProperties: VerifyProperties
) {

    @GetMapping(ApiPaths.VERIFY)
    fun verify(
        @PathVariable @Pattern(regexp = "^($VERIFICATION_TYPE_NV|$VERIFICATION_TYPE_SMV)$") verificationType: String,
        @PathVariable @Pattern(regexp = ApiPaths.MSISDN_PATTERN) msisdn: String,
        request: HttpServletRequest
    ): ResponseEntity<ClientVerifyResponse> {
        log.debug(
            "Verify request: MSISDN: {}, VerificationType: {}, X-Forwarded-For: {}, RemoteAddr: {}, RemotePort: {}",
            msisdn,
            verificationType,
            request.getHeader("X-Forwarded-For"),
            request.remoteAddr,
            request.remotePort
        )

        val response = startVerification(verificationType, msisdn, request)

        log.debug("Verify response: {}", response)

        return ResponseEntity.ok(response)
    }

    private fun startVerification(
        verificationType: String,
        msisdn: String,
        request: HttpServletRequest
    ): ClientVerifyResponse =
        when (verificationType) {
            VERIFICATION_TYPE_NV -> {
                val clientId = verifyProperties.camaraClientId?.takeIf { it.isNotBlank() }
                    ?: throw RequestRejectedException("Camara client ID is not configured for Camara Number Verify")
                numberVerifyService.startVerification(msisdn, clientId)
            }

            VERIFICATION_TYPE_SMV -> {
                val deviceIpAddress = resolveIpAddress(request, verifyProperties.usingProxy)
                silentMobileVerificationService.startVerification(
                    msisdn,
                    deviceIpAddress.address,
                    deviceIpAddress.port
                )
            }

            else -> throw RequestRejectedException("Unsupported verification type")
        }

    @GetMapping(ApiPaths.AUTHORIZE_COMPLETE)
    fun authorizeComplete(
        @RequestParam state: String,
        @RequestParam code: String?,
        @RequestParam error: String?,
        @RequestParam(name = "error_description") errorDescription: String?,
    ): ResponseEntity<Void> {
        log.debug(
            "Verify authorize complete request: state: {}, code: {}, error: {}, errorDescription: {}",
            state,
            code,
            error,
            errorDescription
        )

        if (code.isNullOrBlank() || error != null) {
            numberVerifyService.failAuthorization(state, error, errorDescription)
            return ResponseEntity.unprocessableContent().build()
        }

        numberVerifyService.completeAuthorization(state, code)
        return ResponseEntity.noContent().build()
    }

    @PostMapping(ApiPaths.CALLBACK, consumes = [APPLICATION_JSON_VALUE])
    fun callback(@Valid @RequestBody callbackRequest: CallbackRequest): ResponseEntity<Void> {
        log.debug("Verify callback for token: {} with result: {}", callbackRequest.token, callbackRequest.result)

        silentMobileVerificationService.completeFromCallback(callbackRequest)
        return ResponseEntity.ok().build()
    }

    @GetMapping(ApiPaths.RESULT)
    fun result(@PathVariable token: String): ResponseEntity<ClientVerifyResultResponse> {
        log.debug("Got verify result request for token: {}", token)
        val verifyResult = resultStore.find(token) ?: return ResponseEntity.noContent().build()
        log.debug("Found verify result: {} for token: {}", verifyResult, token)

        return ResponseEntity.ok(
            ClientVerifyResultResponse(
                result = verifyResult.status,
                token = verifyResult.token,
                errorDescription = verifyResult.errorDescription
            )
        )
    }
}
