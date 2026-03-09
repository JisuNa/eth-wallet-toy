package com.ethwallet.core.kms

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class EnvelopeEncryptionService(
    private val kmsClient: KmsClient,
) {

    private val dekCache = Caffeine.newBuilder()
        .maximumSize(DEK_CACHE_MAX_SIZE)
        .expireAfterWrite(DEK_CACHE_TTL_MINUTES, TimeUnit.MINUTES)
        .build<String, ByteArray>()

    fun encrypt(plaintext: String): String {
        val dataKey = kmsClient.generateDataKey()
        val encryptedData = encryptWithDek(plaintext.toByteArray(), dataKey.plaintext)

        val encryptedKeyBase64 = Base64.getEncoder().encodeToString(dataKey.ciphertext)
        val encryptedDataBase64 = Base64.getEncoder().encodeToString(encryptedData)

        return "$encryptedKeyBase64$DELIMITER$encryptedDataBase64"
    }

    fun decrypt(ciphertext: String): String {
        val parts = ciphertext.split(DELIMITER)
        require(parts.size == 2) { "Invalid ciphertext format" }

        val encryptedKey = Base64.getDecoder().decode(parts[0])
        val encryptedData = Base64.getDecoder().decode(parts[1])

        val dekCacheKey = parts[0]
        val dek = dekCache.get(dekCacheKey) { kmsClient.decrypt(encryptedKey) }

        return String(decryptWithDek(encryptedData, dek))
    }

    private fun encryptWithDek(data: ByteArray, dek: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, AES_ALGORITHM), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }
        return iv + cipher.doFinal(data)
    }

    private fun decryptWithDek(data: ByteArray, dek: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = data.copyOfRange(GCM_IV_LENGTH, data.size)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, AES_ALGORITHM), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }
        return cipher.doFinal(encrypted)
    }

    companion object {
        private const val AES_ALGORITHM = "AES"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val DELIMITER = ":"
        private const val DEK_CACHE_MAX_SIZE = 1000L
        private const val DEK_CACHE_TTL_MINUTES = 30L
    }
}
