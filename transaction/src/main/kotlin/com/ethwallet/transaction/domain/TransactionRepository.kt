package com.ethwallet.transaction.domain

import org.springframework.data.jpa.repository.JpaRepository

interface TransactionRepository : JpaRepository<Transaction, Long> {
    fun findByTxId(txId: String): Transaction?
}
