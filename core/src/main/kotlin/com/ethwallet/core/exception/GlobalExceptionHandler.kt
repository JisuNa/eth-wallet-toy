package com.ethwallet.core.exception

import com.ethwallet.core.response.BaseResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BaseException::class)
    fun handleBaseException(e: BaseException): ResponseEntity<BaseResponse> {
        logger.warn { "Business exception: ${e.errorCode.message}" }
        return ResponseEntity
            .status(e.errorCode.status)
            .body(BaseResponse(code = e.errorCode.name, message = e.errorCode.message))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<BaseResponse> {
        logger.error(e) { "Unexpected exception" }
        return ResponseEntity
            .status(ErrorCode.INTERNAL_ERROR.status)
            .body(BaseResponse(code = ErrorCode.INTERNAL_ERROR.name, message = ErrorCode.INTERNAL_ERROR.message))
    }
}
