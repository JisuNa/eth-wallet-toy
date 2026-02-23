package com.example.wallet.application.service

import com.example.wallet.domain.exception.BaseException
import com.example.wallet.domain.exception.ErrorCode
import com.example.wallet.domain.model.Wallet
import com.example.wallet.domain.model.WalletStatus
import com.example.wallet.domain.repository.WalletRepository
import com.example.wallet.infrastructure.web3.EthKeyPair
import com.example.wallet.infrastructure.web3.Web3jKeyPairGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.web3j.crypto.ECKeyPair
import java.math.BigInteger

class WalletServiceTest : BehaviorSpec({

    val walletRepository = mockk<WalletRepository>()
    val keyPairGenerator = mockk<Web3jKeyPairGenerator>()
    val walletService = WalletService(walletRepository, keyPairGenerator)

    val mockAddress = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD38"
    val mockKeyPair = EthKeyPair(
        address = mockAddress,
        privateKey = ECKeyPair.create(BigInteger("12345")),
    )

    Given("신규 사용자의 지갑 생성 요청이 주어졌을 때") {
        val userId = 1L
        every { walletRepository.existsByUserId(userId) } returns false
        every { keyPairGenerator.generate() } returns mockKeyPair
        every { walletRepository.save(any<Wallet>()) } answers { firstArg() }

        When("지갑을 생성하면") {
            val wallet = walletService.addWallet(userId)

            Then("지갑이 저장된다") {
                verify(exactly = 1) { walletRepository.save(any<Wallet>()) }
            }

            Then("올바른 userId와 ACTIVE 상태의 지갑이 반환된다") {
                wallet.userId shouldBe userId
                wallet.status shouldBe WalletStatus.ACTIVE
                wallet.address shouldBe mockAddress
            }
        }
    }

    Given("이미 지갑이 존재하는 사용자의 생성 요청이 주어졌을 때") {
        val userId = 2L
        every { walletRepository.existsByUserId(userId) } returns true

        When("지갑을 생성하면") {
            Then("WALLET_002 예외가 발생한다") {
                val exception = shouldThrow<BaseException> {
                    walletService.addWallet(userId)
                }
                exception.errorCode shouldBe ErrorCode.WALLET_002
            }
        }
    }
})
