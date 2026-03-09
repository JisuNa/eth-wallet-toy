package com.ethwallet.core.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "Wallet not found"),
    DUPLICATE_ADDRESS(HttpStatus.CONFLICT, "Wallet address already exists"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
}
