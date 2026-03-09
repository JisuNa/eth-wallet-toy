package com.ethwallet.core.kms

interface KmsClient {
    fun generateDataKey(): DataKeyResult
    fun decrypt(encryptedKey: ByteArray): ByteArray
}

data class DataKeyResult(
    val plaintext: ByteArray,
    val ciphertext: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DataKeyResult) return false
        return plaintext.contentEquals(other.plaintext) && ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        return 31 * plaintext.contentHashCode() + ciphertext.contentHashCode()
    }
}
