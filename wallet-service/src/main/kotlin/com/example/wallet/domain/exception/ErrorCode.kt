package com.example.wallet.domain.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    WALLET_001(HttpStatus.NOT_FOUND, "Wallet not found"),
    WALLET_002(HttpStatus.CONFLICT, "Wallet already exists for this user"),
    WALLET_003(HttpStatus.BAD_REQUEST, "Invalid request"),
}
