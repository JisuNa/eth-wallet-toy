package com.example.wallet.infrastructure.repository

import com.example.wallet.domain.model.Ledger
import org.springframework.data.jpa.repository.JpaRepository

interface LedgerRepository : JpaRepository<Ledger, Long> {

    fun findByWalletIdOrderByIdDesc(walletId: Long): List<Ledger>

    fun findByTransactionId(transactionId: Long): List<Ledger>
}
