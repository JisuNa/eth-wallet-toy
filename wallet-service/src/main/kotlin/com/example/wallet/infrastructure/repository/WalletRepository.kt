package com.example.wallet.infrastructure.repository

import com.example.wallet.domain.model.Wallet
import org.springframework.data.jpa.repository.JpaRepository

interface WalletRepository : JpaRepository<Wallet, Long> {

    fun existsByMemberIdAndSymbol(memberId: Long, symbol: String): Boolean

    fun findByMemberIdAndSymbol(memberId: Long, symbol: String): Wallet?

    fun findByAddressBlindIndex(blindIndex: String): Wallet?

    fun existsByAddressBlindIndex(blindIndex: String): Boolean
}
