package com.ethwallet.transaction.service

import com.ethwallet.core.exception.BaseException
import com.ethwallet.core.exception.ErrorCode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Component
class WalletClient(
    private val walletRestClient: RestClient,
) {

    private val circuitBreaker = CircuitBreaker.of(
        CIRCUIT_BREAKER_NAME,
        CircuitBreakerConfig.custom()
            .failureRateThreshold(FAILURE_RATE_THRESHOLD)
            .waitDurationInOpenState(Duration.ofSeconds(WAIT_DURATION_SECONDS))
            .slidingWindowSize(SLIDING_WINDOW_SIZE)
            .build(),
    )

    fun deductBalance(walletId: String, amount: BigDecimal) {
        try {
            circuitBreaker.executeCallable {
                walletRestClient.post()
                    .uri("/internal/v1/wallets/{walletId}/deduct", walletId)
                    .body(mapOf("amount" to amount.toPlainString()))
                    .retrieve()
                    .toBodilessEntity()
            }
        } catch (e: BaseException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to call wallet-service: ${e.message}" }
            throw BaseException(ErrorCode.SERVICE_UNAVAILABLE)
        }
    }

    companion object {
        private const val CIRCUIT_BREAKER_NAME = "walletService"
        private const val FAILURE_RATE_THRESHOLD = 50f
        private const val WAIT_DURATION_SECONDS = 30L
        private const val SLIDING_WINDOW_SIZE = 10
    }
}
