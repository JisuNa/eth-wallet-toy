package com.example.wallet.domain.exception

class BaseException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)
