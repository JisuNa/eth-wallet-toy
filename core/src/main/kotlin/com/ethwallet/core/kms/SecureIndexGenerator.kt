package com.ethwallet.core.kms

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class SecureIndexGenerator(
    @Value("\${encryption.blind-index.secret}")
    private val secret: String,
) {

    fun generateIndex(value: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM).apply {
            init(SecretKeySpec(secret.toByteArray(), HMAC_ALGORITHM))
        }

        return Base64.getEncoder().encodeToString(mac.doFinal(value.toByteArray()))
    }

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }
}
