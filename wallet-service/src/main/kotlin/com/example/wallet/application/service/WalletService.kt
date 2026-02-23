package com.example.wallet.application.service

import com.example.wallet.domain.exception.BaseException
import com.example.wallet.domain.exception.ErrorCode
import com.example.wallet.domain.model.Wallet
import com.example.wallet.domain.repository.WalletRepository
import com.example.wallet.infrastructure.web3.Web3jKeyPairGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WalletService(
    private val walletRepository: WalletRepository,
    private val keyPairGenerator: Web3jKeyPairGenerator,
) {

    @Transactional
    fun addWallet(userId: Long): Wallet {
        if (walletRepository.existsByUserId(userId)) {
            throw BaseException(ErrorCode.WALLET_002)
        }

        val keyPair = keyPairGenerator.generate()
        val wallet = Wallet.create(userId, keyPair.address)
        return walletRepository.save(wallet)
    }
}
