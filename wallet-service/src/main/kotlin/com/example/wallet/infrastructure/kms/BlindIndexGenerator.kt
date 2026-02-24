package com.example.wallet.infrastructure.kms

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class BlindIndexGenerator(
    @Value("\${wallet.security.hmac-secret}") private val hmacSecret: String,
) {

    fun generateIndex(plaintext: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(hmacSecret.toByteArray(), HMAC_ALGORITHM))

        return mac.doFinal(plaintext.lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }
}
