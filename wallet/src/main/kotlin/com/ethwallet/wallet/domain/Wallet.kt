package com.ethwallet.wallet.domain

import com.ethwallet.core.domain.WalletStatus
import com.ethwallet.core.jpa.BaseAuditEntity
import com.ethwallet.core.kms.EncryptedStringConverter
import com.ethwallet.core.exception.BaseException
import com.ethwallet.core.exception.ErrorCode
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "wallets")
class Wallet(
    val walletId: String,

    @Convert(converter = EncryptedStringConverter::class)
    val address: String,

    @Convert(converter = EncryptedStringConverter::class)
    val privateKey: String,

    val addressBlind: String,

    @Column(precision = 30, scale = 18)
    var balance: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    var status: WalletStatus = WalletStatus.ACTIVE,
) : BaseAuditEntity() {

    fun deductBalance(amount: BigDecimal) {
        if (balance < amount) {
            throw BaseException(ErrorCode.INSUFFICIENT_BALANCE)
        }
        balance = balance.subtract(amount)
    }

    companion object {
        fun create(address: String, privateKey: String, addressBlind: String): Wallet {
            return Wallet(
                walletId = UUID.randomUUID().toString(),
                address = address,
                privateKey = privateKey,
                addressBlind = addressBlind,
            )
        }
    }
}
