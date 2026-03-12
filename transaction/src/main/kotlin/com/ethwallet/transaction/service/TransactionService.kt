package com.ethwallet.transaction.service

import com.ethwallet.core.domain.TransactionStatus
import com.ethwallet.core.domain.TransactionType
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
}
