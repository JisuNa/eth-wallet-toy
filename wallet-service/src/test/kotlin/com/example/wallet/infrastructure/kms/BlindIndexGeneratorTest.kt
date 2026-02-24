package com.example.wallet.infrastructure.kms

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch

class BlindIndexGeneratorTest : DescribeSpec({

    val service = BlindIndexGenerator(hmacSecret = "test-hmac-secret-key")

    describe("BlindIndex 생성") {
        it("동일한 입력에 대해 동일한 출력을 반환한다") {
            val input = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD38"

            val result1 = service.generateIndex(input)
            val result2 = service.generateIndex(input)

            result1 shouldBe result2
        }

        it("64자리 16진수 문자열을 반환한다") {
            val result = service.generateIndex("test-input")

            result shouldHaveLength 64
            result shouldMatch Regex("^[0-9a-f]{64}$")
        }

        it("다른 입력에 대해 다른 출력을 반환한다") {
            val result1 = service.generateIndex("input-1")
            val result2 = service.generateIndex("input-2")

            result1 shouldNotBe result2
        }
    }
})
