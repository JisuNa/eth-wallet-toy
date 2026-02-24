package com.example.wallet.domain.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    WALLET_001(HttpStatus.NOT_FOUND, "지갑을 찾을 수 없습니다"),
    WALLET_002(HttpStatus.CONFLICT, "이미 해당 심볼의 지갑이 존재합니다"),
    WALLET_003(HttpStatus.BAD_REQUEST, "잘못된 요청입니다"),
    WALLET_004(HttpStatus.BAD_REQUEST, "비활성화된 지갑입니다"),
    WALLET_005(HttpStatus.INTERNAL_SERVER_ERROR, "KMS 암호화에 실패했습니다"),
    WALLET_006(HttpStatus.INTERNAL_SERVER_ERROR, "KMS 복호화에 실패했습니다"),
    WALLET_007(HttpStatus.INTERNAL_SERVER_ERROR, "트랜잭션 서명에 실패했습니다"),
}
