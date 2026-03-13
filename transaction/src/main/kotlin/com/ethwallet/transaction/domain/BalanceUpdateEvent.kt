package com.ethwallet.transaction.domain

import com.ethwallet.core.jpa.BaseAuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "balance_update_event")
class BalanceUpdateEvent(
    val eventId: String,

    val walletId: String,

    @Column(precision = 30, scale = 18)
    val amount: BigDecimal,

    val operation: String,
) : BaseAuditEntity() {

    companion object {
        fun create(
            walletId: String,
            amount: BigDecimal,
            operation: String,
        ): BalanceUpdateEvent {
            return BalanceUpdateEvent(
                eventId = UUID.randomUUID().toString(),
                walletId = walletId,
                amount = amount,
                operation = operation,
            )
        }
    }
}
