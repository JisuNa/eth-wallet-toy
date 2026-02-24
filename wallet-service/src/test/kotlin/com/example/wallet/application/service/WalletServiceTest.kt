package com.example.wallet.application.service

import com.example.wallet.domain.exception.BaseException
import com.example.wallet.domain.exception.ErrorCode
import com.example.wallet.domain.model.Wallet
import com.example.wallet.domain.model.WalletStatus
import com.example.wallet.infrastructure.repository.WalletRepository
import com.example.wallet.infrastructure.kms.BlindIndexGenerator
import com.example.wallet.infrastructure.kms.KmsEncryptor
import com.example.wallet.infrastructure.web3.EthKeyPair
import com.example.wallet.infrastructure.web3.Web3jKeyPairGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigInteger


class WalletServiceTest : BehaviorSpec({

    val walletRepository = mockk<WalletRepository>()
    val keyPairGenerator = mockk<Web3jKeyPairGenerator>()
    val kmsEncryptor = mockk<KmsEncryptor>()
    val blindIndexGenerator = mockk<BlindIndexGenerator>()
    val walletService = WalletService(walletRepository, keyPairGenerator, kmsEncryptor, blindIndexGenerator)

    val mockAddress = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD38"
    val mockEncryptedAddress = "encryptedAddress123"
    val mockBlindIndex = "a".repeat(64)
    val mockEncryptedPrivateKey = "encryptedPK456"
    val mockEcKeyPair = ECKeyPair.create(BigInteger("12345"))
    val mockKeyPair = EthKeyPair(address = mockAddress, privateKey = mockEcKeyPair)
    val mockSymbol = "ETH"

    Given("신규 사용자의 지갑 생성 요청이 주어졌을 때") {
        val memberId = 1L
        every { walletRepository.existsByMemberIdAndSymbol(memberId, mockSymbol) } returns false
        every { keyPairGenerator.generate() } returns mockKeyPair
        every { kmsEncryptor.encrypt(mockAddress) } returns mockEncryptedAddress
        every { blindIndexGenerator.generateIndex(mockAddress) } returns mockBlindIndex
        every { kmsEncryptor.encryptBytes(any()) } returns mockEncryptedPrivateKey
        every { walletRepository.save(any<Wallet>()) } answers { firstArg() }

        When("지갑을 생성하면") {
            val response = walletService.addWallet(memberId, mockSymbol)

            Then("키 생성, 암호화, 블라인드 인덱스 생성, 저장이 수행된다") {
                verify(exactly = 1) { keyPairGenerator.generate() }
                verify(exactly = 1) { kmsEncryptor.encrypt(mockAddress) }
                verify(exactly = 1) { blindIndexGenerator.generateIndex(mockAddress) }
                verify(exactly = 1) { kmsEncryptor.encryptBytes(any()) }
                verify(exactly = 1) { walletRepository.save(any<Wallet>()) }
            }

            Then("생성된 지갑의 WalletDto가 반환된다") {
                response.wallet.memberId shouldBe memberId
                response.wallet.symbol shouldBe mockSymbol
                response.address shouldBe mockAddress
            }
        }
    }

    Given("이미 동일 심볼의 지갑이 존재하는 사용자의 생성 요청이 주어졌을 때") {
        val memberId = 2L
        every { walletRepository.existsByMemberIdAndSymbol(memberId, mockSymbol) } returns true

        When("지갑을 생성하면") {
            Then("WALLET_002 예외가 발생한다") {
                val exception = shouldThrow<BaseException> {
                    walletService.addWallet(memberId, mockSymbol)
                }
                exception.errorCode shouldBe ErrorCode.WALLET_002
            }
        }
    }

    Given("존재하는 memberId와 symbol로 조회 요청이 주어졌을 때") {
        val memberId = 1L
        val wallet = Wallet.create(
            memberId = memberId,
            symbol = mockSymbol,
            encryptedAddress = mockEncryptedAddress,
            addressBlindIndex = mockBlindIndex,
            encryptedPrivateKey = mockEncryptedPrivateKey,
        )
        every { walletRepository.findByMemberIdAndSymbol(memberId, mockSymbol) } returns wallet
        every { kmsEncryptor.decrypt(mockEncryptedAddress) } returns mockAddress

        When("지갑을 조회하면") {
            val response = walletService.getWallet(memberId, mockSymbol)

            Then("복호화된 주소와 함께 WalletDto가 반환된다") {
                response.address shouldBe mockAddress
                response.wallet.memberId shouldBe memberId
                response.wallet.symbol shouldBe mockSymbol
                response.wallet.status shouldBe WalletStatus.ACTIVE
            }
        }
    }

    Given("존재하지 않는 memberId와 symbol로 조회 요청이 주어졌을 때") {
        val memberId = 999L
        every { walletRepository.findByMemberIdAndSymbol(memberId, mockSymbol) } returns null

        When("지갑을 조회하면") {
            Then("WALLET_001 예외가 발생한다") {
                val exception = shouldThrow<BaseException> {
                    walletService.getWallet(memberId, mockSymbol)
                }
                exception.errorCode shouldBe ErrorCode.WALLET_001
            }
        }
    }

    Given("존재하는 주소로 검색 요청이 주어졌을 때") {
        val wallet = Wallet.create(
            memberId = 1L,
            symbol = mockSymbol,
            encryptedAddress = mockEncryptedAddress,
            addressBlindIndex = mockBlindIndex,
            encryptedPrivateKey = mockEncryptedPrivateKey,
        )
        every { blindIndexGenerator.generateIndex(mockAddress) } returns mockBlindIndex
        every { walletRepository.findByAddressBlindIndex(mockBlindIndex) } returns wallet
        every { kmsEncryptor.decrypt(mockEncryptedAddress) } returns mockAddress

        When("주소로 검색하면") {
            val response = walletService.searchWallet(mockAddress)

            Then("블라인드 인덱스로 조회 후 복호화된 WalletDto가 반환된다") {
                response.address shouldBe mockAddress
                response.wallet.memberId shouldBe 1L
                response.wallet.symbol shouldBe mockSymbol
                verify { blindIndexGenerator.generateIndex(mockAddress) }
                verify { kmsEncryptor.decrypt(mockEncryptedAddress) }
            }
        }
    }

    Given("존재하지 않는 주소로 검색 요청이 주어졌을 때") {
        val unknownAddress = "0x0000000000000000000000000000000000000000"
        val unknownBlindIndex = "b".repeat(64)
        every { blindIndexGenerator.generateIndex(unknownAddress) } returns unknownBlindIndex
        every { walletRepository.findByAddressBlindIndex(unknownBlindIndex) } returns null

        When("주소로 검색하면") {
            Then("WALLET_001 예외가 발생한다") {
                val exception = shouldThrow<BaseException> {
                    walletService.searchWallet(unknownAddress)
                }
                exception.errorCode shouldBe ErrorCode.WALLET_001
            }
        }
    }

    Given("존재하는 주소로 존재 여부 확인 요청이 주어졌을 때") {
        every { blindIndexGenerator.generateIndex(mockAddress) } returns mockBlindIndex
        every { walletRepository.existsByAddressBlindIndex(mockBlindIndex) } returns true

        When("존재 여부를 확인하면") {
            val result = walletService.existsByAddress(mockAddress)

            Then("true가 반환된다") {
                result shouldBe true
            }
        }
    }

    Given("존재하지 않는 주소로 존재 여부 확인 요청이 주어졌을 때") {
        val unknownAddress = "0x0000000000000000000000000000000000000000"
        val unknownBlindIndex = "b".repeat(64)
        every { blindIndexGenerator.generateIndex(unknownAddress) } returns unknownBlindIndex
        every { walletRepository.existsByAddressBlindIndex(unknownBlindIndex) } returns false

        When("존재 여부를 확인하면") {
            val result = walletService.existsByAddress(unknownAddress)

            Then("false가 반환된다") {
                result shouldBe false
            }
        }
    }

    Given("ACTIVE 상태의 지갑으로 서명 요청이 주어졌을 때") {
        val memberId = 1L
        val ecKeyPair = ECKeyPair.create(BigInteger("12345"))
        val privateKeyBytes = Numeric.toBytesPadded(ecKeyPair.privateKey, 32)
        val wallet = Wallet.create(
            memberId = memberId,
            symbol = mockSymbol,
            encryptedAddress = mockEncryptedAddress,
            addressBlindIndex = mockBlindIndex,
            encryptedPrivateKey = mockEncryptedPrivateKey,
        )

        val rawTransaction = RawTransaction.createEtherTransaction(
            BigInteger.ONE,
            BigInteger.valueOf(21000),
            BigInteger.valueOf(1000000000),
            "0x0000000000000000000000000000000000000001",
            BigInteger.valueOf(1000000000000000000),
        )
        val unsignedTxBytes = TransactionEncoder.encode(rawTransaction)
        val unsignedTx = Numeric.toHexString(unsignedTxBytes)

        every { walletRepository.findByMemberIdAndSymbol(memberId, mockSymbol) } returns wallet
        every { kmsEncryptor.decryptToBytes(mockEncryptedPrivateKey) } returns privateKeyBytes

        When("트랜잭션을 서명하면") {
            val response = walletService.signTransaction(memberId, mockSymbol, unsignedTx)

            Then("서명된 트랜잭션이 반환된다") {
                response.startsWith("0x") shouldBe true
                (response.length > unsignedTx.length) shouldBe true
            }
        }
    }

    Given("존재하지 않는 지갑으로 서명 요청이 주어졌을 때") {
        val memberId = 999L
        every { walletRepository.findByMemberIdAndSymbol(memberId, mockSymbol) } returns null

        When("트랜잭션을 서명하면") {
            Then("WALLET_001 예외가 발생한다") {
                val exception = shouldThrow<BaseException> {
                    walletService.signTransaction(memberId, mockSymbol, "0x1234")
                }
                exception.errorCode shouldBe ErrorCode.WALLET_001
            }
        }
    }

    Given("FROZEN 상태의 지갑으로 서명 요청이 주어졌을 때") {
        val memberId = 1L
        val wallet = Wallet.create(
            memberId = memberId,
            symbol = mockSymbol,
            encryptedAddress = mockEncryptedAddress,
            addressBlindIndex = mockBlindIndex,
            encryptedPrivateKey = mockEncryptedPrivateKey,
        )
        wallet.status = WalletStatus.FROZEN

        every { walletRepository.findByMemberIdAndSymbol(memberId, mockSymbol) } returns wallet

        When("트랜잭션을 서명하면") {
            Then("WALLET_004 예외가 발생한다") {
                val exception = shouldThrow<BaseException> {
                    walletService.signTransaction(memberId, mockSymbol, "0x1234")
                }
                exception.errorCode shouldBe ErrorCode.WALLET_004
            }
        }
    }
})
