package com.ethwallet.wallet.dto

import com.ethwallet.wallet.domain.Wallet

data class WalletResponse(
    val walletId: String,
    val address: String,
    val status: String,
) {

    companion object {
        fun from(wallet: Wallet): WalletResponse {
            return WalletResponse(
                walletId = wallet.walletId,
                address = wallet.address,
                status = wallet.status.name,
            )
        }
    }
}
