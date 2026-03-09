package com.ethwallet.core.kms

import org.springframework.stereotype.Component
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class LocalKmsClient : KmsClient {

    private val masterKey: SecretKey = SecretKeySpec(
        System.getenv("KMS_MASTER_KEY")?.let { java.util.Base64.getDecoder().decode(it) }
            ?: SecureRandom().let { random ->
                ByteArray(32).also { random.nextBytes(it) }
            },
        AES_ALGORITHM,
    )

    override fun generateDataKey(): DataKeyResult {
        val plaintext = ByteArray(DEK_SIZE).also { SecureRandom().nextBytes(it) }
        val ciphertext = encryptWithMasterKey(plaintext)
        return DataKeyResult(plaintext = plaintext, ciphertext = ciphertext)
    }

    override fun decrypt(encryptedKey: ByteArray): ByteArray {
        return decryptWithMasterKey(encryptedKey)
    }

    private fun encryptWithMasterKey(data: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }
        val encrypted = cipher.doFinal(data)
        return iv + encrypted
    }

    private fun decryptWithMasterKey(data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = data.copyOfRange(GCM_IV_LENGTH, data.size)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }
        return cipher.doFinal(encrypted)
    }

    companion object {
        private const val AES_ALGORITHM = "AES"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val DEK_SIZE = 32
    }
}
