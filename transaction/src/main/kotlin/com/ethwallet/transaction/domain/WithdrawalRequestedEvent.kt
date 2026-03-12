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
@Table(name = "withdrawal_requested_event")
@EntityListeners(AuditingEntityListener::class)
class WithdrawalRequestedEvent(
    val eventId: String,

    val txId: String,

    val walletId: String,

    val toAddress: String,

    @Column(precision = 30, scale = 18)
    val amount: BigDecimal,
) : BaseEntity() {

    @CreatedDate
    @Column(updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.MIN
        protected set

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
