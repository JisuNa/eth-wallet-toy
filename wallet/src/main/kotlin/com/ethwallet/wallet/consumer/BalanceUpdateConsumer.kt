package com.ethwallet.wallet.consumer

import com.ethwallet.core.redis.IdempotencyChecker
import com.ethwallet.wallet.service.WalletService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class BalanceUpdateConsumer(
    private val walletService: WalletService,
    private val idempotencyChecker: IdempotencyChecker,
    private val objectMapper: ObjectMapper,
) {

    @KafkaListener(topics = [TOPIC], groupId = GROUP_ID)
    fun consume(message: String, acknowledgment: Acknowledgment) {
        val payload = extractPayload(message)
        val eventId = payload.get(FIELD_EVENT_ID)?.asText()
            ?: throw IllegalArgumentException("Missing required field: $FIELD_EVENT_ID")

        if (idempotencyChecker.checkAndMarkProcessing(eventId) is IdempotencyChecker.IdempotencyResult.Completed) {
            logger.info { "Duplicate event skipped: $eventId" }
            acknowledgment.acknowledge()
            return
        }

        val walletId = payload.get(FIELD_WALLET_ID)?.asText()
            ?: throw IllegalArgumentException("Missing required field: $FIELD_WALLET_ID")
        val amount = payload.get(FIELD_AMOUNT)?.asText()?.toBigDecimal()
            ?: throw IllegalArgumentException("Missing required field: $FIELD_AMOUNT")
        val operation = payload.get(FIELD_OPERATION)?.asText()
            ?: throw IllegalArgumentException("Missing required field: $FIELD_OPERATION")

        logger.info { "Balance update event received: walletId=$walletId, amount=$amount, operation=$operation" }

        try {
            when (operation) {
                OPERATION_CREDIT -> walletService.addBalance(walletId, amount)
                OPERATION_DEBIT -> walletService.deductBalance(walletId, amount)
                else -> logger.warn { "Unknown operation: $operation" }
            }
        } catch (e: Exception) {
            idempotencyChecker.remove(eventId)
            throw e
        }

        idempotencyChecker.markCompleted(eventId, operation)
        acknowledgment.acknowledge()
    }

    private fun extractPayload(message: String): JsonNode {
        val root = objectMapper.readTree(message)

        val payload = root.get(FIELD_PAYLOAD)
        if (payload != null && payload.has(FIELD_AFTER)) {
            return payload.get(FIELD_AFTER)
        }

        return root
    }

    companion object {
        private const val TOPIC = "wallet.balance.update"
        private const val GROUP_ID = "wallet-service"
        private const val FIELD_EVENT_ID = "event_id"
        private const val FIELD_WALLET_ID = "wallet_id"
        private const val FIELD_AMOUNT = "amount"
        private const val FIELD_OPERATION = "operation"
        private const val FIELD_PAYLOAD = "payload"
        private const val FIELD_AFTER = "after"
        private const val OPERATION_CREDIT = "CREDIT"
        private const val OPERATION_DEBIT = "DEBIT"
    }
}
