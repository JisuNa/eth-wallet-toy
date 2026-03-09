package com.ethwallet.core.exception

class BaseException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)
