package com.ethwallet.wallet.service

import com.ethwallet.core.kms.SecureIndexGenerator
import com.ethwallet.wallet.domain.Wallet
import com.ethwallet.wallet.domain.WalletRepository
import com.ethwallet.wallet.dto.WalletResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.web3j.crypto.Keys

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
}
