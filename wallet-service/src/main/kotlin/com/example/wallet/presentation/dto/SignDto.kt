package com.example.wallet.presentation.dto

import jakarta.validation.constraints.NotBlank

data class SignRequest(
    @field:NotBlank
    val unsignedTx: String,
)

data class SignResponse(
    val signedTx: String,
)
