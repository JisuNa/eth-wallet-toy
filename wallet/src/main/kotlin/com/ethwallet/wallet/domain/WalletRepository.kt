package com.ethwallet.wallet.domain

import org.springframework.data.jpa.repository.JpaRepository

interface WalletRepository : JpaRepository<Wallet, Long> {
    fun findByWalletId(walletId: String): Wallet?
    fun findByAddressBlind(addressBlind: String): Wallet?
}
