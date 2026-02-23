package com.example.wallet.domain.model

import com.example.wallet.infrastructure.entity.BaseAuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "wallet")
class Wallet(
    val userId: Long,

    @Column(unique = true)
    val address: String,

    @Column(precision = 38, scale = 18)
    var balance: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    var status: WalletStatus = WalletStatus.ACTIVE,

) : BaseAuditEntity() {

    companion object {
        fun create(userId: Long, address: String): Wallet {
            require(userId > 0) { "userId must be positive" }
            require(address.startsWith("0x") && address.length == 42) { "invalid ethereum address" }
            return Wallet(
                userId = userId,
                address = address,
            )
        }
    }
}
