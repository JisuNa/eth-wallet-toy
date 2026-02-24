package com.example.wallet.application.dto

import com.example.wallet.domain.model.Wallet

data class WalletDto(
    val wallet: Wallet,
    val address: String,
)
