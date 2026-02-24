package com.example.wallet.infrastructure.kms

import com.example.wallet.domain.exception.BaseException
import com.example.wallet.domain.exception.ErrorCode
import com.example.wallet.infrastructure.config.KmsProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DecryptRequest
import software.amazon.awssdk.services.kms.model.DecryptResponse
import software.amazon.awssdk.services.kms.model.EncryptRequest
import software.amazon.awssdk.services.kms.model.EncryptResponse
import software.amazon.awssdk.services.kms.model.KmsException
import java.util.Base64

class KmsEncryptorTest : BehaviorSpec({

    val kmsClient = mockk<KmsClient>()
    val kmsProperties = mockk<KmsProperties>()
    val testKeyAlias = "alias/test-data-key"

    every { kmsProperties.dataKeyAlias } returns testKeyAlias

    val service = KmsEncryptor(kmsClient, kmsProperties)

    Given("평문 텍스트가 주어졌을 때") {
        val plaintext = "0xABC123"
        val encryptedBytes = "encrypted-data".toByteArray()

        every { kmsClient.encrypt(any<EncryptRequest>()) } returns EncryptResponse.builder()
            .ciphertextBlob(SdkBytes.fromByteArray(encryptedBytes))
            .build()

        When("encrypt를 호출하면") {
            val result = service.encrypt(plaintext)

            Then("Base64로 인코딩된 암호문이 반환된다") {
                val expected = Base64.getEncoder().encodeToString(encryptedBytes)
                result shouldBe expected
            }

            Then("KMS 클라이언트가 올바른 keyId로 호출된다") {
                verify {
                    kmsClient.encrypt(match<EncryptRequest> {
                        it.keyId() == testKeyAlias
                    })
                }
            }
        }
    }

    Given("Base64로 인코딩된 암호문이 주어졌을 때") {
        val originalText = "0xABC123"
        val ciphertextBase64 = Base64.getEncoder().encodeToString("encrypted-data".toByteArray())

        every { kmsClient.decrypt(any<DecryptRequest>()) } returns DecryptResponse.builder()
            .plaintext(SdkBytes.fromUtf8String(originalText))
            .build()

        When("decrypt를 호출하면") {
            val result = service.decrypt(ciphertextBase64)

            Then("원본 평문이 반환된다") {
                result shouldBe originalText
            }

            Then("KMS 클라이언트가 호출된다") {
                verify { kmsClient.decrypt(any<DecryptRequest>()) }
            }
        }
    }

    Given("바이트 배열이 주어졌을 때") {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val encryptedBytes = "encrypted-bytes".toByteArray()

        every { kmsClient.encrypt(any<EncryptRequest>()) } returns EncryptResponse.builder()
            .ciphertextBlob(SdkBytes.fromByteArray(encryptedBytes))
            .build()

        When("encryptBytes를 호출하면") {
            val result = service.encryptBytes(bytes)

            Then("Base64로 인코딩된 암호문이 반환된다") {
                val expected = Base64.getEncoder().encodeToString(encryptedBytes)
                result shouldBe expected
            }
        }
    }

    Given("Base64로 인코딩된 바이트 암호문이 주어졌을 때") {
        val originalBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val ciphertextBase64 = Base64.getEncoder().encodeToString("encrypted-bytes".toByteArray())

        every { kmsClient.decrypt(any<DecryptRequest>()) } returns DecryptResponse.builder()
            .plaintext(SdkBytes.fromByteArray(originalBytes))
            .build()

        When("decryptToBytes를 호출하면") {
            val result = service.decryptToBytes(ciphertextBase64)

            Then("원본 바이트 배열이 반환된다") {
                result shouldBe originalBytes
            }
        }
    }

    Given("KMS 암호화 중 오류가 발생했을 때") {
        every { kmsClient.encrypt(any<EncryptRequest>()) } throws KmsException.builder()
            .message("KMS error")
            .build()

        When("encrypt를 호출하면") {
            Then("WALLET_005 예외가 발생한다") {
                val exception = shouldThrow<BaseException> {
                    service.encrypt("plaintext")
                }
                exception.errorCode shouldBe ErrorCode.WALLET_005
            }
        }
    }

    Given("KMS 복호화 중 오류가 발생했을 때") {
        val ciphertextBase64 = Base64.getEncoder().encodeToString("some-data".toByteArray())

        every { kmsClient.decrypt(any<DecryptRequest>()) } throws KmsException.builder()
            .message("KMS error")
            .build()

        When("decrypt를 호출하면") {
            Then("WALLET_006 예외가 발생한다") {
                val exception = shouldThrow<BaseException> {
                    service.decrypt(ciphertextBase64)
                }
                exception.errorCode shouldBe ErrorCode.WALLET_006
            }
        }
    }
})
