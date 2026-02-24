package com.example.wallet.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class WalletTest : DescribeSpec({

    val validBlindIndex = "a".repeat(64)

    describe("Wallet 생성") {
        it("유효한 암호화 필드로 Wallet을 생성하면 ACTIVE 상태와 0 잔액을 가진다") {
            val wallet = Wallet.create(
                memberId = 1L,
                symbol = "ETH",
                encryptedAddress = "enc-address",
                addressBlindIndex = validBlindIndex,
                encryptedPrivateKey = "enc-private-key",
            )

            wallet.memberId shouldBe 1L
            wallet.symbol shouldBe "ETH"
            wallet.encryptedAddress shouldBe "enc-address"
            wallet.addressBlindIndex shouldBe validBlindIndex
            wallet.encryptedPrivateKey shouldBe "enc-private-key"
            wallet.status shouldBe WalletStatus.ACTIVE
            wallet.balance shouldBeEqualComparingTo BigDecimal.ZERO
        }

        it("memberId가 0 이하이면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Wallet.create(
                    memberId = 0L,
                    symbol = "ETH",
                    encryptedAddress = "enc-address",
                    addressBlindIndex = validBlindIndex,
                    encryptedPrivateKey = "enc-private-key",
                )
            }
        }

        it("음수 memberId로 생성하면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Wallet.create(
                    memberId = -1L,
                    symbol = "ETH",
                    encryptedAddress = "enc-address",
                    addressBlindIndex = validBlindIndex,
                    encryptedPrivateKey = "enc-private-key",
                )
            }
        }

        it("symbol이 빈 문자열이면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Wallet.create(
                    memberId = 1L,
                    symbol = "",
                    encryptedAddress = "enc-address",
                    addressBlindIndex = validBlindIndex,
                    encryptedPrivateKey = "enc-private-key",
                )
            }
        }

        it("symbol이 공백만 포함하면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Wallet.create(
                    memberId = 1L,
                    symbol = "   ",
                    encryptedAddress = "enc-address",
                    addressBlindIndex = validBlindIndex,
                    encryptedPrivateKey = "enc-private-key",
                )
            }
        }

        it("encryptedAddress가 빈 문자열이면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Wallet.create(
                    memberId = 1L,
                    symbol = "ETH",
                    encryptedAddress = "",
                    addressBlindIndex = validBlindIndex,
                    encryptedPrivateKey = "enc-private-key",
                )
            }
        }

        it("addressBlindIndex 길이가 64가 아니면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Wallet.create(
                    memberId = 1L,
                    symbol = "ETH",
                    encryptedAddress = "enc-address",
                    addressBlindIndex = "short-index",
                    encryptedPrivateKey = "enc-private-key",
                )
            }
        }

        it("encryptedPrivateKey가 빈 문자열이면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Wallet.create(
                    memberId = 1L,
                    symbol = "ETH",
                    encryptedAddress = "enc-address",
                    addressBlindIndex = validBlindIndex,
                    encryptedPrivateKey = "",
                )
            }
        }
    }

    describe("WalletStatus") {
        it("ACTIVE 상태는 isActive가 true이다") {
            WalletStatus.ACTIVE.isActive() shouldBe true
        }

        it("FROZEN 상태는 isActive가 false이다") {
            WalletStatus.FROZEN.isActive() shouldBe false
        }

        it("DEACTIVATED 상태는 isActive가 false이다") {
            WalletStatus.DEACTIVATED.isActive() shouldBe false
        }
    }
})
