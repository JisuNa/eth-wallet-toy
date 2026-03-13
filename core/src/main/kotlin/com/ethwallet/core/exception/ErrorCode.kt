package com.ethwallet.core.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "Wallet not found"),
    DUPLICATE_ADDRESS(HttpStatus.CONFLICT, "Wallet address already exists"),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "Insufficient balance"),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required"),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Transaction not found"),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "Invalid status transition"),
    CONCURRENT_ACCESS(HttpStatus.CONFLICT, "Concurrent access detected"),
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "Request is already in progress"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service is temporarily unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
}
