package com.ethwallet.core.redis

import com.ethwallet.core.exception.BaseException
import com.ethwallet.core.exception.ErrorCode
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

class IdempotencyChecker(
    private val redisTemplate: StringRedisTemplate,
) {

    fun checkAndMarkProcessing(idempotencyKey: String): IdempotencyResult {
        val key = "$KEY_PREFIX$idempotencyKey"

        val set = redisTemplate.opsForValue().setIfAbsent(key, STATUS_PROCESSING, TTL)
        if (set == true) {
            return IdempotencyResult.New
        }

        val existing = redisTemplate.opsForValue().get(key)
            ?: return IdempotencyResult.New.also {
                redisTemplate.opsForValue().setIfAbsent(key, STATUS_PROCESSING, TTL)
            }

        if (existing.startsWith(STATUS_COMPLETED_PREFIX)) {
            val cachedResponse = existing.removePrefix(STATUS_COMPLETED_PREFIX)
            return IdempotencyResult.Completed(cachedResponse)
        }

        throw BaseException(ErrorCode.REQUEST_IN_PROGRESS)
    }

    fun markCompleted(idempotencyKey: String, response: String) {
        val key = "$KEY_PREFIX$idempotencyKey"
        redisTemplate.opsForValue().set(key, "$STATUS_COMPLETED_PREFIX$response", TTL)
    }

    fun remove(idempotencyKey: String) {
        val key = "$KEY_PREFIX$idempotencyKey"
        redisTemplate.delete(key)
    }

    sealed interface IdempotencyResult {
        data object New : IdempotencyResult
        data class Completed(val cachedResponse: String) : IdempotencyResult
    }

    companion object {
        private const val KEY_PREFIX = "idempotency:"
        private const val STATUS_PROCESSING = "PROCESSING"
        private const val STATUS_COMPLETED_PREFIX = "COMPLETED:"
        private val TTL = Duration.ofHours(24)
    }
}
