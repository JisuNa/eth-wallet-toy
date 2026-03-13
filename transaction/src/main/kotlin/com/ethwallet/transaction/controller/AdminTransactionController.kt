package com.ethwallet.transaction.controller

import com.ethwallet.core.response.SingleResponse
import com.ethwallet.transaction.dto.TransactionResponse
import com.ethwallet.transaction.service.TransactionFacade
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin/api/v1/transactions")
class AdminTransactionController(
    private val transactionFacade: TransactionFacade,
) {

    @PutMapping("/{txId}/approve", name = "출금 승인")
    fun approveWithdrawal(
        @PathVariable txId: String,
    ): SingleResponse<TransactionResponse> {
        return SingleResponse.of(transactionFacade.approveWithdrawal(txId))
    }

    @PutMapping("/{txId}/reject", name = "출금 거부")
    fun rejectWithdrawal(
        @PathVariable txId: String,
    ): SingleResponse<TransactionResponse> {
        return SingleResponse.of(transactionFacade.rejectWithdrawal(txId))
    }
}
