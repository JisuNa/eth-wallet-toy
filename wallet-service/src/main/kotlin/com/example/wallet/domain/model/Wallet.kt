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
    val memberId: Long,

    @Column(length = 10)
    val symbol: String,

    @Column(columnDefinition = "TEXT")
    val encryptedAddress: String,

    @Column(length = 64, unique = true)
    val addressBlindIndex: String,

    @Column(columnDefinition = "TEXT")
    val encryptedPrivateKey: String,

    @Column(precision = 38, scale = 18)
    var balance: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    var status: WalletStatus = WalletStatus.ACTIVE,
) : BaseAuditEntity() {

    fun isNotActive(): Boolean = status.isNotActive()

    companion object {
        private const val BLIND_INDEX_LENGTH = 64

        fun create(
            memberId: Long,
            symbol: String,
            encryptedAddress: String,
            addressBlindIndex: String,
            encryptedPrivateKey: String,
        ): Wallet {
            require(memberId > 0) { "memberId must be positive" }
            require(symbol.isNotBlank()) { "symbol must not be blank" }
            require(encryptedAddress.isNotBlank()) { "encryptedAddress must not be blank" }
            require(addressBlindIndex.length == BLIND_INDEX_LENGTH) { "addressBlindIndex must be $BLIND_INDEX_LENGTH characters" }
            require(encryptedPrivateKey.isNotBlank()) { "encryptedPrivateKey must not be blank" }

            return Wallet(
                memberId = memberId,
                symbol = symbol,
                encryptedAddress = encryptedAddress,
                addressBlindIndex = addressBlindIndex,
                encryptedPrivateKey = encryptedPrivateKey,
            )
        }
    }
}
