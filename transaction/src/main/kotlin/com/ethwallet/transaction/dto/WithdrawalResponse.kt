package com.ethwallet.transaction.dto

import com.ethwallet.transaction.domain.Transaction
import java.time.LocalDateTime

data class WithdrawalResponse(
    val txId: String,
    val status: String,
    val amount: String,
    val createdAt: LocalDateTime,
)

fun Transaction.toWithdrawalResponse(): WithdrawalResponse {
    return WithdrawalResponse(
        txId = txId,
        status = status.name,
        amount = amount.toPlainString(),
        createdAt = createdAt,
    )
}
