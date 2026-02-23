package com.example.wallet.presentation.dto

import jakarta.validation.constraints.Positive

data class CreateWalletRequest(
    @field:Positive
    val userId: Long,
)
