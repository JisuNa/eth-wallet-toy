package com.example.wallet.presentation.advice

import com.example.wallet.domain.exception.BaseException
import com.example.wallet.presentation.common.BaseResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BaseException::class)
    fun handleBaseException(e: BaseException): ResponseEntity<BaseResponse> {
        log.warn("Business exception: {} - {}", e.errorCode.name, e.message)
        return ResponseEntity
            .status(e.errorCode.status)
            .body(BaseResponse(code = e.errorCode.name, message = e.errorCode.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<BaseResponse> {
        val message = e.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity
            .badRequest()
            .body(BaseResponse(code = "VALIDATION_ERROR", message = message))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<BaseResponse> {
        log.error("Unexpected error", e)
        return ResponseEntity
            .internalServerError()
            .body(BaseResponse(code = "INTERNAL_ERROR", message = "Internal server error"))
    }
}
