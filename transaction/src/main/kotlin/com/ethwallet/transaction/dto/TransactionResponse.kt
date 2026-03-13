package com.ethwallet.transaction.dto

import com.ethwallet.transaction.domain.Transaction
import java.time.LocalDateTime

data class TransactionResponse(
    val txId: String,
    val walletId: String,
    val status: String,
    val amount: String,
    val type: String,
    val createdAt: LocalDateTime,
)

fun Transaction.toTransactionResponse(): TransactionResponse {
    return TransactionResponse(
        txId = txId,
        walletId = walletId,
        status = status.name,
        amount = amount.toPlainString(),
        type = type.name,
        createdAt = createdAt,
    )
}
