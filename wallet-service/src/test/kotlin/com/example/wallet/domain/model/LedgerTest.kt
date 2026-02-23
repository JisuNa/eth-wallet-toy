package com.example.wallet.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class LedgerTest : DescribeSpec({

    describe("Ledger 생성") {
        it("유효한 파라미터로 Ledger를 생성한다") {
            val entry = Ledger.create(
                walletId = 1L,
                transactionId = 100L,
                type = LedgerType.DEPOSIT,
                amount = BigDecimal("1.5"),
                balanceBefore = BigDecimal("10.0"),
                balanceAfter = BigDecimal("11.5"),
            )

            entry.walletId shouldBe 1L
            entry.transactionId shouldBe 100L
            entry.type shouldBe LedgerType.DEPOSIT
            entry.amount shouldBeEqualComparingTo BigDecimal("1.5")
            entry.balanceBefore shouldBeEqualComparingTo BigDecimal("10.0")
            entry.balanceAfter shouldBeEqualComparingTo BigDecimal("11.5")
        }

        it("walletId가 0 이하이면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Ledger.create(
                    walletId = 0L,
                    transactionId = 1L,
                    type = LedgerType.DEPOSIT,
                    amount = BigDecimal("1.0"),
                    balanceBefore = BigDecimal.ZERO,
                    balanceAfter = BigDecimal("1.0"),
                )
            }
        }

        it("transactionId가 0 이하이면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Ledger.create(
                    walletId = 1L,
                    transactionId = 0L,
                    type = LedgerType.DEPOSIT,
                    amount = BigDecimal("1.0"),
                    balanceBefore = BigDecimal.ZERO,
                    balanceAfter = BigDecimal("1.0"),
                )
            }
        }

        it("amount가 0 이하이면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Ledger.create(
                    walletId = 1L,
                    transactionId = 1L,
                    type = LedgerType.WITHDRAWAL,
                    amount = BigDecimal.ZERO,
                    balanceBefore = BigDecimal("10.0"),
                    balanceAfter = BigDecimal("10.0"),
                )
            }
        }

        it("balanceBefore가 음수이면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Ledger.create(
                    walletId = 1L,
                    transactionId = 1L,
                    type = LedgerType.DEPOSIT,
                    amount = BigDecimal("1.0"),
                    balanceBefore = BigDecimal("-1.0"),
                    balanceAfter = BigDecimal.ZERO,
                )
            }
        }

        it("balanceAfter가 음수이면 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Ledger.create(
                    walletId = 1L,
                    transactionId = 1L,
                    type = LedgerType.WITHDRAWAL,
                    amount = BigDecimal("1.0"),
                    balanceBefore = BigDecimal("0.5"),
                    balanceAfter = BigDecimal("-0.5"),
                )
            }
        }
    }

    describe("LedgerType") {
        it("DEPOSIT, WITHDRAWAL, COMPENSATION 세 가지 타입이 존재한다") {
            LedgerType.entries.map { it.name } shouldBe listOf("DEPOSIT", "WITHDRAWAL", "COMPENSATION")
        }
    }
})
