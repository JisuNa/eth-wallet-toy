package com.example.wallet.application.service

import com.example.wallet.application.dto.WalletDto
import com.example.wallet.domain.exception.BaseException
import com.example.wallet.domain.exception.ErrorCode
import com.example.wallet.domain.model.Wallet
import com.example.wallet.infrastructure.repository.WalletRepository
import com.example.wallet.infrastructure.kms.BlindIndexGenerator
import com.example.wallet.infrastructure.kms.KmsEncryptor
import com.example.wallet.infrastructure.web3.Web3jKeyPairGenerator
import com.example.wallet.infrastructure.config.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.TransactionDecoder
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigInteger

@Service
class WalletService(
    private val walletRepository: WalletRepository,
    private val keyPairGenerator: Web3jKeyPairGenerator,
    private val kmsEncryptor: KmsEncryptor,
    private val blindIndexGenerator: BlindIndexGenerator,
) {

    @Transactional
    fun addWallet(memberId: Long, symbol: String): WalletDto {
        val normalizedSymbol = symbol.uppercase()

        if (walletRepository.existsByMemberIdAndSymbol(memberId, normalizedSymbol)) {
            throw BaseException(ErrorCode.WALLET_002)
        }

        val keyPair = keyPairGenerator.generate()

        val encryptedAddress = kmsEncryptor.encrypt(keyPair.address)
        val addressBlindIndex = blindIndexGenerator.generateIndex(keyPair.address)
        val encryptedPrivateKey = kmsEncryptor.encryptBytes(
            Numeric.toBytesPadded(keyPair.privateKey.privateKey, PRIVATE_KEY_BYTES)
        )

        val wallet = Wallet.create(
            memberId = memberId,
            symbol = normalizedSymbol,
            encryptedAddress = encryptedAddress,
            addressBlindIndex = addressBlindIndex,
            encryptedPrivateKey = encryptedPrivateKey,
        )

        val savedWallet = walletRepository.save(wallet)

        return WalletDto(savedWallet, keyPair.address)
    }

    @Transactional(readOnly = true)
    fun getWallet(memberId: Long, symbol: String): WalletDto {
        val normalizedSymbol = symbol.uppercase()

        val wallet = walletRepository.findByMemberIdAndSymbol(memberId, normalizedSymbol)
            ?: throw BaseException(ErrorCode.WALLET_001)

        val decryptedAddress = kmsEncryptor.decrypt(wallet.encryptedAddress)

        return WalletDto(wallet, decryptedAddress)
    }

    @Transactional(readOnly = true)
    fun searchWallet(address: String): WalletDto {
        val blindIndex = blindIndexGenerator.generateIndex(address)

        val wallet = walletRepository.findByAddressBlindIndex(blindIndex)
            ?: throw BaseException(ErrorCode.WALLET_001)

        val decryptedAddress = kmsEncryptor.decrypt(wallet.encryptedAddress)

        if (!decryptedAddress.equals(address, ignoreCase = true)) {
            throw BaseException(ErrorCode.WALLET_001)
        }

        return WalletDto(wallet, decryptedAddress)
    }

    @Transactional(readOnly = true)
    fun existsByAddress(address: String): Boolean {
        val blindIndex = blindIndexGenerator.generateIndex(address)

        return walletRepository.existsByAddressBlindIndex(blindIndex)
    }

    @Transactional(readOnly = true)
    fun signTransaction(memberId: Long, symbol: String, unsignedTx: String): String {
        val normalizedSymbol = symbol.uppercase()

        val wallet = walletRepository.findByMemberIdAndSymbol(memberId, normalizedSymbol)
            ?: throw BaseException(ErrorCode.WALLET_001)

        if (wallet.isNotActive()) {
            throw BaseException(ErrorCode.WALLET_004)
        }

        val privateKeyBytes = kmsEncryptor.decryptToBytes(wallet.encryptedPrivateKey)

        try {
            val ecKeyPair = ECKeyPair.create(BigInteger(1, privateKeyBytes))
            val credentials = Credentials.create(ecKeyPair)

            val rawTransaction = TransactionDecoder.decode(unsignedTx)
            val signedBytes = TransactionEncoder.signMessage(rawTransaction, credentials)

            return Numeric.toHexString(signedBytes)
        } catch (e: BaseException) {
            throw e
        } catch (e: Exception) {
            logger.error { "Transaction signing failed for memberId=$memberId, symbol=$normalizedSymbol: ${e.message}" }
            throw BaseException(ErrorCode.WALLET_007)
        } finally {
            privateKeyBytes.fill(0)
        }
    }

    companion object {
        private val logger = logger()
        private const val PRIVATE_KEY_BYTES = 32
    }
}
