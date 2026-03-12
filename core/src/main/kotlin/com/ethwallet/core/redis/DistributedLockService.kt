package com.ethwallet.core.redis

import com.ethwallet.core.exception.BaseException
import com.ethwallet.core.exception.ErrorCode
import org.redisson.api.RedissonClient
import java.util.concurrent.TimeUnit

class DistributedLockService(
    private val redissonClient: RedissonClient,
) {

    fun <T> executeWithLock(key: String, action: () -> T): T {
        val lock = redissonClient.getLock(key)

        val acquired = lock.tryLock(WAIT_TIME_SECONDS, LEASE_TIME_SECONDS, TimeUnit.SECONDS)
        if (!acquired) {
            throw BaseException(ErrorCode.CONCURRENT_ACCESS)
        }

        try {
            return action()
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    companion object {
        private const val WAIT_TIME_SECONDS = 0L
        private const val LEASE_TIME_SECONDS = 10L
    }
}
