package com.infobip.napi.api

import com.infobip.napi.api.models.numberverify.ClientVerifyResultResponse
import com.infobip.napi.service.RequestRejectedException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

private const val INVALID_REQUEST_DESCRIPTION = "Invalid request"

@RestControllerAdvice(basePackageClasses = [ApiExceptionHandler::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler {

    @ExceptionHandler
    fun requestRejected(exception: RequestRejectedException): ResponseEntity<ClientVerifyResultResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ClientVerifyResultResponse(token = exception.token, errorDescription = exception.description))

    @ExceptionHandler
    fun invalidParameters(exception: HandlerMethodValidationException): ResponseEntity<ClientVerifyResultResponse> =
        invalidRequest(exception.parameterValidationResults.mapNotNull { it.methodParameter.parameterName })

    @ExceptionHandler
    fun invalidBody(exception: MethodArgumentNotValidException): ResponseEntity<ClientVerifyResultResponse> =
        invalidRequest(exception.bindingResult.fieldErrors.map { it.field })

    @ExceptionHandler
    fun unreadableBody(exception: HttpMessageNotReadableException): ResponseEntity<ClientVerifyResultResponse> =
        invalidRequest(emptyList())

    private fun invalidRequest(names: List<String>): ResponseEntity<ClientVerifyResultResponse> {
        val description = names.distinct().takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "$INVALID_REQUEST_DESCRIPTION: ")
            ?: INVALID_REQUEST_DESCRIPTION
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ClientVerifyResultResponse(errorDescription = description))
    }
}
