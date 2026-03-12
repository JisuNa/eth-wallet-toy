package com.ethwallet.core.redis

import org.redisson.api.RedissonClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
@ConditionalOnProperty(prefix = "spring.data.redis", name = ["host"])
class RedisConfig {

    @Bean
    fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate {
        return StringRedisTemplate(connectionFactory)
    }

    @Bean
    fun distributedLockService(redissonClient: RedissonClient): DistributedLockService {
        return DistributedLockService(redissonClient)
    }

    @Bean
    fun idempotencyChecker(stringRedisTemplate: StringRedisTemplate): IdempotencyChecker {
        return IdempotencyChecker(stringRedisTemplate)
    }
}
