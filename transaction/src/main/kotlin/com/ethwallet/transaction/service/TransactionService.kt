package com.ethwallet.transaction.service

import com.ethwallet.core.domain.TransactionStatus
import com.ethwallet.core.domain.TransactionType
import com.ethwallet.core.exception.BaseException
import com.ethwallet.core.exception.ErrorCode
import com.ethwallet.transaction.domain.BalanceUpdateEvent
import com.ethwallet.transaction.domain.BalanceUpdateEventRepository
import com.ethwallet.transaction.domain.Transaction
import com.ethwallet.transaction.domain.TransactionRepository
import com.ethwallet.transaction.domain.WithdrawalRequestedEvent
import com.ethwallet.transaction.domain.WithdrawalRequestedEventRepository
import com.ethwallet.transaction.dto.WithdrawalRequest
import com.ethwallet.transaction.dto.WithdrawalResponse
import com.ethwallet.transaction.dto.toWithdrawalResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val withdrawalRequestedEventRepository: WithdrawalRequestedEventRepository,
    private val balanceUpdateEventRepository: BalanceUpdateEventRepository,
) {

    @Transactional
    fun createTransaction(request: WithdrawalRequest, amount: BigDecimal, status: TransactionStatus): WithdrawalResponse {
        val transaction = Transaction.create(
            walletId = request.walletId,
            toAddress = request.toAddress,
            amount = amount,
            type = TransactionType.WITHDRAWAL,
            status = status,
        )
        transactionRepository.save(transaction)

        if (status == TransactionStatus.PENDING) {
            val event = WithdrawalRequestedEvent.create(
                txId = transaction.txId,
                walletId = transaction.walletId,
                toAddress = request.toAddress,
                amount = amount,
            )
            withdrawalRequestedEventRepository.save(event)
        }

        return transaction.toWithdrawalResponse()
    }

    @Transactional
    fun approveTransaction(txId: String): Transaction {
        val transaction = transactionRepository.findByTxId(txId)
            ?: throw BaseException(ErrorCode.TRANSACTION_NOT_FOUND)

        if (transaction.status.isNotPendingApproval()) {
            throw BaseException(ErrorCode.INVALID_STATUS_TRANSITION)
        }

        transaction.approve()

        val event = WithdrawalRequestedEvent.create(
            txId = transaction.txId,
            walletId = transaction.walletId,
            toAddress = transaction.toAddress,
            amount = transaction.amount,
        )
        withdrawalRequestedEventRepository.save(event)

        return transaction
    }

    @Transactional
    fun rejectTransaction(txId: String): Transaction {
        val transaction = transactionRepository.findByTxId(txId)
            ?: throw BaseException(ErrorCode.TRANSACTION_NOT_FOUND)

        if (transaction.status.isNotPendingApproval()) {
            throw BaseException(ErrorCode.INVALID_STATUS_TRANSITION)
        }

        transaction.reject()

        val event = BalanceUpdateEvent.create(
            walletId = transaction.walletId,
            amount = transaction.amount,
            operation = OPERATION_CREDIT,
        )
        balanceUpdateEventRepository.save(event)

        return transaction
    }

    companion object {
        private const val OPERATION_CREDIT = "CREDIT"
    }
}
