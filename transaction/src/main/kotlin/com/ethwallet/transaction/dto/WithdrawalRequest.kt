package com.ethwallet.transaction.dto

data class WithdrawalRequest(
    val walletId: String,
    val toAddress: String,
    val amount: String,
)
