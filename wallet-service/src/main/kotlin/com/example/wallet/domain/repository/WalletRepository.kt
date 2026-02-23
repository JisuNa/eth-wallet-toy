package com.example.wallet.domain.repository

import com.example.wallet.domain.model.Wallet
import org.springframework.data.jpa.repository.JpaRepository

interface WalletRepository : JpaRepository<Wallet, Long> {

    fun existsByUserId(userId: Long): Boolean
}
