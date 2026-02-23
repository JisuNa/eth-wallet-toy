package com.example.wallet.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class WalletTest : DescribeSpec({

    describe("Wallet 생성") {
        it("유효한 userId와 address로 Wallet을 생성하면 ACTIVE 상태와 0 잔액을 가진다") {
            val wallet = Wallet.create(userId = 1L, address = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD38")

            wallet.userId shouldBe 1L
            wallet.status shouldBe WalletStatus.ACTIVE
            wallet.balance shouldBeEqualComparingTo BigDecimal.ZERO
            wallet.address shouldBe "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD38"
        }

        it("userId가 0 이하이면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Wallet.create(userId = 0L, address = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD38")
            }
        }

        it("음수 userId로 생성하면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Wallet.create(userId = -1L, address = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD38")
            }
        }

        it("0x로 시작하지 않는 주소이면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Wallet.create(userId = 1L, address = "742d35Cc6634C0532925a3b844Bc9e7595f2bD38")
            }
        }

        it("길이가 42가 아닌 주소이면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Wallet.create(userId = 1L, address = "0x742d35Cc")
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
