package com.ethwallet.transaction.domain

import com.ethwallet.core.domain.TransactionStatus
import com.ethwallet.core.domain.TransactionType
import com.ethwallet.core.jpa.BaseModifyAuditEntity
import com.ethwallet.core.kms.EncryptedStringConverter
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "transactions")
class Transaction(
    val txId: String,

    val walletId: String,

    @Convert(converter = EncryptedStringConverter::class)
    val toAddress: String,

    @Column(precision = 30, scale = 18)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    val type: TransactionType,

    @Enumerated(EnumType.STRING)
    var status: TransactionStatus,

    var txHash: String? = null,
) : BaseModifyAuditEntity() {

    fun approve() {
        status = TransactionStatus.PENDING
    }

    fun reject() {
        status = TransactionStatus.REJECTED
    }

    companion object {
        fun create(
            walletId: String,
            toAddress: String,
            amount: BigDecimal,
            type: TransactionType,
            status: TransactionStatus,
        ): Transaction {
            return Transaction(
                txId = UUID.randomUUID().toString(),
                walletId = walletId,
                toAddress = toAddress,
                amount = amount,
                type = type,
                status = status,
            )
        }
    }
}
