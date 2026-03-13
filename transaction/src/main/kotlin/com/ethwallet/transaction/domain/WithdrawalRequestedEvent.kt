package com.ethwallet.transaction.domain

import com.ethwallet.core.jpa.BaseAuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "withdrawal_requested_event")
class WithdrawalRequestedEvent(
    val eventId: String,

    val txId: String,

    val walletId: String,

    val toAddress: String,

    @Column(precision = 30, scale = 18)
    val amount: BigDecimal,
) : BaseAuditEntity() {

    companion object {
        fun create(
            txId: String,
            walletId: String,
            toAddress: String,
            amount: BigDecimal,
        ): WithdrawalRequestedEvent {
            return WithdrawalRequestedEvent(
                eventId = UUID.randomUUID().toString(),
                txId = txId,
                walletId = walletId,
                toAddress = toAddress,
                amount = amount,
            )
        }
    }
}
