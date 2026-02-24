package com.example.wallet.infrastructure.kms

import com.example.wallet.domain.exception.BaseException
import com.example.wallet.domain.exception.ErrorCode
import com.example.wallet.infrastructure.config.KmsProperties
import com.example.wallet.infrastructure.config.logger
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DecryptRequest
import software.amazon.awssdk.services.kms.model.EncryptRequest
import software.amazon.awssdk.services.kms.model.KmsException
import java.util.Base64

@Service
class KmsEncryptor(
    private val kmsClient: KmsClient,
    private val kmsProperties: KmsProperties,
) {

    fun encrypt(plaintext: String): String {
        return encryptBytes(plaintext.toByteArray(Charsets.UTF_8))
    }

    fun decrypt(ciphertextBase64: String): String {
        return String(decryptToBytes(ciphertextBase64), Charsets.UTF_8)
    }

    fun encryptBytes(bytes: ByteArray): String {
        try {
            val request = EncryptRequest.builder()
                .keyId(kmsProperties.dataKeyAlias)
                .plaintext(SdkBytes.fromByteArray(bytes))
                .build()

            return Base64.getEncoder().encodeToString(
                kmsClient.encrypt(request).ciphertextBlob().asByteArray()
            )
        } catch (e: KmsException) {
            logger.error(e) { "KMS encryption failed: ${e.message}" }
            throw BaseException(ErrorCode.WALLET_005)
        }
    }

    fun decryptToBytes(ciphertextBase64: String): ByteArray {
        try {
            val request = DecryptRequest.builder()
                .ciphertextBlob(SdkBytes.fromByteArray(Base64.getDecoder().decode(ciphertextBase64)))
                .build()

            return kmsClient.decrypt(request).plaintext().asByteArray()

        } catch (e: KmsException) {
            logger.error(e) { "KMS decryption failed: ${e.message}" }
            throw BaseException(ErrorCode.WALLET_006)
        }
    }

    companion object {
        private val logger = logger()
    }
}
