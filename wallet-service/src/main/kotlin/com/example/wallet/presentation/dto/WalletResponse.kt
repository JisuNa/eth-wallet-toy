package com.example.wallet.presentation.dto

import com.example.wallet.domain.model.Wallet
import com.example.wallet.domain.model.WalletStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class WalletResponse(
    val memberId: Long,
    val symbol: String,
    val address: String,
    val balance: BigDecimal,
    val status: WalletStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

fun Wallet.toResponse(decryptedAddress: String): WalletResponse = WalletResponse(
    memberId = memberId,
    symbol = symbol,
    address = decryptedAddress,
    balance = balance,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
