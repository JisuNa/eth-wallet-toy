package com.ethwallet.transaction.domain

import org.springframework.data.jpa.repository.JpaRepository

interface WithdrawalRequestedEventRepository : JpaRepository<WithdrawalRequestedEvent, Long>
