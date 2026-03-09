package com.ethwallet.core.exception

import com.ethwallet.core.response.BaseResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BaseException::class)
    fun handleBaseException(e: BaseException): ResponseEntity<BaseResponse> {
        log.warn("Business exception: {}", e.errorCode.message)
        return ResponseEntity
            .status(e.errorCode.status)
            .body(BaseResponse(code = e.errorCode.name, message = e.errorCode.message))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<BaseResponse> {
        log.error("Unexpected exception", e)
        return ResponseEntity
            .status(ErrorCode.INTERNAL_ERROR.status)
            .body(BaseResponse(code = ErrorCode.INTERNAL_ERROR.name, message = ErrorCode.INTERNAL_ERROR.message))
    }
}
