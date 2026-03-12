package com.ethwallet.transaction.service

import com.ethwallet.core.domain.TransactionStatus
import com.ethwallet.core.exception.BaseException
import com.ethwallet.core.exception.ErrorCode
import com.ethwallet.core.redis.DistributedLockService
import com.ethwallet.core.redis.IdempotencyChecker
import com.ethwallet.core.redis.IdempotencyChecker.IdempotencyResult
import com.ethwallet.transaction.dto.WithdrawalRequest
import com.ethwallet.transaction.dto.WithdrawalResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class TransactionFacade(
    private val transactionService: TransactionService,
    private val walletClient: WalletClient,
    private val distributedLockService: DistributedLockService,
    private val idempotencyChecker: IdempotencyChecker,
    private val objectMapper: ObjectMapper,
) {

    fun createWithdrawal(idempotencyKey: String?, request: WithdrawalRequest): WithdrawalResponse {
        if (idempotencyKey == null) {
            throw BaseException(ErrorCode.MISSING_IDEMPOTENCY_KEY)
        }

        val idempotencyResult = idempotencyChecker.checkAndMarkProcessing(idempotencyKey)
        if (idempotencyResult is IdempotencyResult.Completed) {
            return objectMapper.readValue(idempotencyResult.cachedResponse, WithdrawalResponse::class.java)
        }

        try {
            val response = distributedLockService.executeWithLock("$WALLET_LOCK_PREFIX${request.walletId}") {
                processWithdrawal(request)
            }

            idempotencyChecker.markCompleted(idempotencyKey, objectMapper.writeValueAsString(response))

            return response
        } catch (e: BaseException) {
            throw e
        } catch (e: Exception) {
            idempotencyChecker.remove(idempotencyKey)
            throw e
        }
    }

    private fun processWithdrawal(request: WithdrawalRequest): WithdrawalResponse {
        val amount = request.amount.toBigDecimal()

        walletClient.deductBalance(request.walletId, amount)

        val needsApproval = amount.multiply(ETH_PRICE_KRW) >= APPROVAL_THRESHOLD_KRW
        val status = if (needsApproval) TransactionStatus.PENDING_APPROVAL else TransactionStatus.PENDING

        return transactionService.createTransaction(request, amount, status)
    }

    companion object {
        private const val WALLET_LOCK_PREFIX = "LOCK:wallet:"
        private val ETH_PRICE_KRW = BigDecimal("5000000")
        private val APPROVAL_THRESHOLD_KRW = BigDecimal("500000")
    }
}
