package com.example.wallet.domain.model

enum class WalletStatus {
    ACTIVE,
    FROZEN,
    DEACTIVATED;

    fun isActive(): Boolean = this == ACTIVE

    fun isNotActive(): Boolean = this != ACTIVE
}
