package com.example.wallet.domain.model

import com.example.wallet.infrastructure.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "ledger")
@EntityListeners(AuditingEntityListener::class)
class Ledger(
    @Column(nullable = false)
    val walletId: Long,

    @Column(nullable = false)
    val transactionId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: LedgerType,

    @Column(nullable = false, precision = 38, scale = 18)
    val amount: BigDecimal,

    @Column(nullable = false, precision = 38, scale = 18)
    val balanceBefore: BigDecimal,

    @Column(nullable = false, precision = 38, scale = 18)
    val balanceAfter: BigDecimal,
) : BaseEntity() {

    @CreatedDate
    @Column(updatable = false, nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.MIN
        protected set

    companion object {
        fun create(
            walletId: Long,
            transactionId: Long,
            type: LedgerType,
            amount: BigDecimal,
            balanceBefore: BigDecimal,
            balanceAfter: BigDecimal,
        ): Ledger {
            require(walletId > 0) { "walletId must be positive" }
            require(transactionId > 0) { "transactionId must be positive" }
            require(amount > BigDecimal.ZERO) { "amount must be positive" }
            require(balanceBefore >= BigDecimal.ZERO) { "balanceBefore must not be negative" }
            require(balanceAfter >= BigDecimal.ZERO) { "balanceAfter must not be negative" }

            return Ledger(
                walletId = walletId,
                transactionId = transactionId,
                type = type,
                amount = amount,
                balanceBefore = balanceBefore,
                balanceAfter = balanceAfter,
            )
        }
    }
}
