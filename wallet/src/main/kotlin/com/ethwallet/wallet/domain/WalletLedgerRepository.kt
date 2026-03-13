package com.ethwallet.wallet.domain

import org.springframework.data.jpa.repository.JpaRepository

interface WalletLedgerRepository : JpaRepository<WalletLedger, Long>
