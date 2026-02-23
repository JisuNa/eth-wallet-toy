package com.example.wallet.presentation.common

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
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
        fun <T> of(data: T): SingleResponse<T> = SingleResponse(data)
    }
}

class ListResponse<T>(
    val data: List<T>,
    code: String = "SUCCESS",
    message: String = "OK",
) : BaseResponse(code, message)

class PageResponse<T>(
    val data: List<T>,
    val total: Long,
    code: String = "SUCCESS",
    message: String = "OK",
) : BaseResponse(code, message)

class NoDataResponse(
    code: String = "SUCCESS",
    message: String = "OK",
) : BaseResponse(code, message) {

    companion object {
        val OK = NoDataResponse()
    }
}
