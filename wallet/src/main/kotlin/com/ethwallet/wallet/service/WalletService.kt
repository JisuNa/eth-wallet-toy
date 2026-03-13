package com.ethwallet.wallet.service

import com.ethwallet.core.kms.SecureIndexGenerator
import com.ethwallet.core.exception.BaseException
import com.ethwallet.core.exception.ErrorCode
import com.ethwallet.wallet.domain.Wallet
import com.ethwallet.wallet.domain.WalletRepository
import com.ethwallet.wallet.dto.WalletResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.web3j.crypto.Keys
import java.math.BigDecimal

@Service
class WalletService(
    private val walletRepository: WalletRepository,
    private val secureIndexGenerator: SecureIndexGenerator,
) {

    @Transactional
    fun addWallet(): WalletResponse {
        val ecKeyPair = Keys.createEcKeyPair()
        val address = "0x${Keys.getAddress(ecKeyPair)}"
        val privateKey = ecKeyPair.privateKey.toString(16)

        val addressBlind = secureIndexGenerator.generateIndex(address)

        val wallet = Wallet.create(
            address = address,
            privateKey = privateKey,
            addressBlind = addressBlind,
        )
        walletRepository.save(wallet)

        return WalletResponse.from(wallet)
    }

    @Transactional
    fun deductBalance(walletId: String, amount: BigDecimal) {
        val wallet = walletRepository.findByWalletId(walletId)
            ?: throw BaseException(ErrorCode.WALLET_NOT_FOUND)

        wallet.deductBalance(amount)
    }

    @Transactional
    fun addBalance(walletId: String, amount: BigDecimal) {
        val wallet = walletRepository.findByWalletId(walletId)
            ?: throw BaseException(ErrorCode.WALLET_NOT_FOUND)

        wallet.addBalance(amount)
    }
}
