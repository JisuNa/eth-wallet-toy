package com.ethwallet.transaction.domain

import com.ethwallet.core.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "balance_update_event")
@EntityListeners(AuditingEntityListener::class)
class BalanceUpdateEvent(
    val eventId: String,

    val walletId: String,

    @Column(precision = 30, scale = 18)
    val amount: BigDecimal,

    val operation: String,
) : BaseEntity() {

    @CreatedDate
    @Column(updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.MIN
        protected set

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
