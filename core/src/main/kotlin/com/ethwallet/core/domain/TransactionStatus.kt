package com.ethwallet.core.domain

enum class TransactionStatus {
    PENDING_APPROVAL, PENDING, PROCESSING, CONFIRMED, REJECTED, FAILED, ROLLBACK;

    fun isNotPendingApproval(): Boolean = this != PENDING_APPROVAL
}
