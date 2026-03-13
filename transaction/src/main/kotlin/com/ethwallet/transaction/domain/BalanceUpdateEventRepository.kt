package com.ethwallet.transaction.domain

import org.springframework.data.jpa.repository.JpaRepository

interface BalanceUpdateEventRepository : JpaRepository<BalanceUpdateEvent, Long>
