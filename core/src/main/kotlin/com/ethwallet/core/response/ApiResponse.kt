package com.ethwallet.core.response

open class BaseResponse(
    val code: String = "SUCCESS",
    val message: String = "OK",
)

class SingleResponse<T>(
    val data: T,
    code: String = "SUCCESS",
    message: String = "OK",
) : BaseResponse(code, message) {

    companion object {
        fun <T> of(data: T): SingleResponse<T> = SingleResponse(data = data)
    }
}
