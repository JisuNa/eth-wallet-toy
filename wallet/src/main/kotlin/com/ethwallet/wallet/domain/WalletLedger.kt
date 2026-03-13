package com.ethwallet.wallet.domain

import com.ethwallet.core.jpa.BaseAuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "wallet_ledgers")
class WalletLedger(
    val walletId: String,

    @Column(precision = 30, scale = 18)
    val amount: BigDecimal,

    val operation: String,

    val description: String,
) : BaseAuditEntity() {

    companion object {
        private const val OPERATION_DEBIT = "DEBIT"
        private const val OPERATION_CREDIT = "CREDIT"
        private const val DESCRIPTION_DEBIT = "잔액 차감"
        private const val DESCRIPTION_CREDIT = "잔액 충전"

        fun deductBalance(walletId: String, amount: BigDecimal): WalletLedger {
            return WalletLedger(
                walletId = walletId,
                amount = amount,
                operation = OPERATION_DEBIT,
                description = DESCRIPTION_DEBIT,
            )
        }

        fun addBalance(walletId: String, amount: BigDecimal): WalletLedger {
            return WalletLedger(
                walletId = walletId,
                amount = amount,
                operation = OPERATION_CREDIT,
                description = DESCRIPTION_CREDIT,
            )
        }
    }
}
