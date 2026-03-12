package com.ethwallet.transaction.controller

import com.ethwallet.core.HttpHeaders
import com.ethwallet.core.response.SingleResponse
import com.ethwallet.transaction.dto.WithdrawalRequest
import com.ethwallet.transaction.dto.WithdrawalResponse
import com.ethwallet.transaction.service.TransactionFacade
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/transactions")
class TransactionController(
    private val transactionFacade: TransactionFacade,
) {

    @PostMapping("/withdrawal", name = "출금 요청")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun createWithdrawal(
        @RequestHeader(HttpHeaders.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
        @RequestBody request: WithdrawalRequest,
    ): SingleResponse<WithdrawalResponse> {
        return SingleResponse.of(transactionFacade.createWithdrawal(idempotencyKey, request))
    }
}
