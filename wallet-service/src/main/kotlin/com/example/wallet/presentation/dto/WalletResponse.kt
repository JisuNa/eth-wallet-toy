package com.example.wallet.presentation.dto

import com.example.wallet.domain.model.Wallet
import com.example.wallet.domain.model.WalletStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class WalletResponse(
    val id: Long,
    val userId: Long,
    val address: String,
    val balance: BigDecimal,
    val status: WalletStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

fun Wallet.toResponse(): WalletResponse = WalletResponse(
    id = id,
    userId = userId,
    address = address,
    balance = balance,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
