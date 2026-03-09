# 5. Fault Tolerance

## 5.1 Circuit Breaker (Resilience4j)

### 적용 대상

| 호출 경로 | 설명 | Fallback |
|----------|------|----------|
| transaction-service → wallet-service (잔액 차감) | 출금 시 잔액 확인/차감 REST 호출 | 즉시 실패 응답 |
| transaction-service → blockchain-service (브로드캐스트) | 트랜잭션 브로드캐스트 | PENDING 유지, 재시도 큐 |

### Circuit Breaker 설정

```yaml
resilience4j:
  circuitbreaker:
    instances:
      wallet-service:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10           # 최근 10건 기준
        minimum-number-of-calls: 5        # 최소 5건 이후 판단
        failure-rate-threshold: 50        # 50% 실패 시 OPEN
        wait-duration-in-open-state: 30s  # OPEN 30초 유지
        permitted-number-of-calls-in-half-open-state: 3
      blockchain-service:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s  # 블록체인은 복구가 느릴 수 있음
        permitted-number-of-calls-in-half-open-state: 3
```

### 상태 전이

```
           성공률 ≥ 50%
    ┌──────────────────┐
    │                  │
    ▼                  │
 CLOSED ──실패률>50%──► OPEN ──30s 경과──► HALF_OPEN
    ▲                                        │
    │              성공률 ≥ 50%               │
    └────────────────────────────────────────┘
                   실패률 > 50%
                   ──► OPEN (다시)
```

### 코드 적용

```kotlin
@Service
class WalletServiceClient(
    private val restTemplate: RestTemplate
) {
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "deductBalanceFallback")
    fun deductBalance(walletId: String, amount: BigDecimal): BalanceResponse {
        return restTemplate.postForObject(
            "http://wallet-service:8081/internal/v1/wallets/$walletId/deduct",
            DeductRequest(amount),
            BalanceResponse::class.java
        )!!
    }

    private fun deductBalanceFallback(
        walletId: String, amount: BigDecimal, ex: Exception
    ): BalanceResponse {
        throw ServiceUnavailableException("wallet-service 일시적 장애. 잠시 후 재시도해주세요.")
    }
}
```

---

## 5.2 분산 락 (Redisson)

### 목적
동일 지갑에 대한 동시 출금 요청 방지. 잔액 차감의 원자성 보장.

### 락 키 패턴

```
LOCK:wallet:{walletId}
```

### 설정

| 파라미터 | 값 | 설명 |
|---------|-----|------|
| Wait Time | 5초 | 락 획득 대기 시간 |
| Lease Time | 30초 | 자동 해제 시간 (데드락 방지) |

### DistributedLockService

```kotlin
@Component
class DistributedLockService(
    private val redissonClient: RedissonClient
) {
    fun <T> executeWithLock(
        key: String,
        waitTime: Long = 5,
        leaseTime: Long = 30,
        timeUnit: TimeUnit = TimeUnit.SECONDS,
        action: () -> T
    ): T {
        val lock = redissonClient.getLock(key)

        if (!lock.tryLock(waitTime, leaseTime, timeUnit)) {
            throw ConcurrentAccessException("동시 처리 중입니다. 잠시 후 재시도해주세요.")
        }

        try {
            return action()
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }
}
```

### 사용 예시 (출금)

```kotlin
@Service
class WithdrawalService(
    private val distributedLockService: DistributedLockService,
    private val walletServiceClient: WalletServiceClient
) {
    fun withdraw(request: WithdrawalRequest): TransactionResponse {
        return distributedLockService.executeWithLock(
            key = "LOCK:wallet:${request.walletId}"
        ) {
            // 1. 잔액 확인 & 차감 (Circuit Breaker 적용)
            walletServiceClient.deductBalance(request.walletId, request.amount)

            // 2. 트랜잭션 생성
            val tx = createTransaction(request)

            // 3. Outbox 이벤트 저장 (같은 DB 트랜잭션)
            saveOutboxEvent(tx)

            tx.toResponse()
        }
    }
}
```

---

## 5.3 Retry 전략

### Kafka Consumer Retry

```
1st retry: 1초 후
2nd retry: 1초 후
3rd retry: 1초 후
→ 3회 실패 시 DLT(Dead Letter Topic)로 이동
```

- DLT 토픽 네이밍: `{원본토픽}.DLT` (예: `wallet.balance.update.DLT`)
- DLT 레코드는 모니터링 후 수동 재처리

### REST 호출 Retry (Resilience4j)

```yaml
resilience4j:
  retry:
    instances:
      wallet-service:
        max-attempts: 3
        wait-duration: 500ms
        retry-exceptions:
          - java.io.IOException
          - org.springframework.web.client.ResourceAccessException
```

---

## 5.4 Fallback 전략

| 장애 상황 | Fallback | 복구 |
|----------|----------|------|
| blockchain-service 장애 | 트랜잭션 PENDING 상태 유지 | 복구 후 PENDING 트랜잭션 재처리 스케줄러 |
| wallet-service 잔액 조회 실패 | Redis 캐시에서 마지막 잔액 반환 | wallet-service 복구 시 캐시 갱신 |
| Kafka 발행 실패 | Outbox 테이블에 남아있음 | 다음 폴링 주기에 재발행 |

### 잔액 캐시 구조

```
Redis Key: wallet:balance:{walletId}
Value: "1.500000000000000000"   (ETH 단위 문자열)
TTL: 5분
```

```kotlin
@Service
class BalanceService(
    private val walletRepository: WalletRepository,
    private val redisTemplate: StringRedisTemplate
) {
    fun getBalance(walletId: String): BigDecimal {
        // 1. DB 조회 시도
        return try {
            val wallet = walletRepository.findByWalletId(walletId)
                ?: throw WalletNotFoundException(walletId)

            // 캐시 갱신
            cacheBalance(walletId, wallet.balance)
            wallet.balance
        } catch (ex: DataAccessException) {
            // 2. DB 장애 시 캐시 fallback
            getCachedBalance(walletId)
                ?: throw ServiceUnavailableException("잔액 조회 일시 불가")
        }
    }
}
```

---

## 5.5 Saga Pattern (출금 롤백)

출금 프로세스에서 블록체인 처리 실패 시 잔액을 복구하는 Choreography Saga.

### 정상 흐름

```
1. [transaction-service] 출금 요청 → 잔액 차감 → tx PENDING
2. [transaction-service] Outbox → Kafka: tx.withdrawal.requested
3. [blockchain-service] 트랜잭션 처리 → 블록에 포함
4. [blockchain-service] Kafka: blockchain.tx.confirmed (status=MINED)
5. [transaction-service] tx CONFIRMED
```

### 실패 & 롤백 흐름

```
1. [transaction-service] 출금 요청 → 잔액 차감 → tx PENDING
2. [transaction-service] Outbox → Kafka: tx.withdrawal.requested
3. [blockchain-service] 트랜잭션 처리 실패
4. [blockchain-service] Kafka: blockchain.tx.confirmed (status=FAILED)
5. [transaction-service] tx FAILED → Outbox: wallet.balance.update (CREDIT)
6. [wallet-service] 잔액 복구 (원래 금액 다시 CREDIT)
7. [transaction-service] tx ROLLBACK
```

---

## 5.6 REST API 멱등성 (출금)

분산 락은 동시 요청만 차단하며, 순차 중복 요청은 막지 못한다.
클라이언트가 네트워크 타임아웃 등으로 동일 출금을 재전송하는 경우를 방지하기 위해
`Idempotency-Key` 헤더 기반 멱등성을 적용한다.

### Redis 저장 구조

```
Key:   idempotency:rest:{idempotency-key}
Value: JSON { "status": "PROCESSING" | "COMPLETED", "response": "..." }
TTL:   24시간
```

### IdempotencyKeyService

```kotlin
@Component
class IdempotencyKeyService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val KEY_PREFIX = "idempotency:rest:"
        private val TTL = Duration.ofHours(24)
    }

    /**
     * 멱등성 키 선점 시도
     * @return null → 신규 요청 (PROCESSING으로 선점됨)
     *         IdempotencyResult → 기존 요청 상태
     */
    fun tryAcquire(idempotencyKey: String): IdempotencyResult? {
        val key = KEY_PREFIX + idempotencyKey
        val value = objectMapper.writeValueAsString(
            IdempotencyEntry(status = "PROCESSING")
        )
        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(key, value, TTL)

        if (acquired == true) return null  // 신규 요청

        // 기존 키 존재 → 상태 확인
        val existing = redisTemplate.opsForValue().get(key)
            ?: return null  // TTL 만료 직후 race condition
        val entry = objectMapper.readValue(existing, IdempotencyEntry::class.java)
        return IdempotencyResult(
            status = entry.status,
            cachedResponse = entry.response
        )
    }

    /** 처리 완료 후 응답 캐싱 */
    fun complete(idempotencyKey: String, responseBody: String) {
        val key = KEY_PREFIX + idempotencyKey
        val value = objectMapper.writeValueAsString(
            IdempotencyEntry(status = "COMPLETED", response = responseBody)
        )
        redisTemplate.opsForValue().set(key, value, TTL)
    }

    /** 처리 실패 시 키 삭제 (재시도 허용) */
    fun release(idempotencyKey: String) {
        redisTemplate.delete(KEY_PREFIX + idempotencyKey)
    }
}

data class IdempotencyEntry(
    val status: String,
    val response: String? = null
)

data class IdempotencyResult(
    val status: String,          // "PROCESSING" | "COMPLETED"
    val cachedResponse: String?  // COMPLETED일 때만 non-null
)
```

### 적용 흐름

```
Client ──Idempotency-Key: {uuid}──► [transaction-service]
                                          │
                                    tryAcquire(key)
                                          │
                          ┌───────────────┼───────────────┐
                          ▼               ▼               ▼
                        null          PROCESSING      COMPLETED
                     (신규 요청)      (처리 중)       (처리 완료)
                          │               │               │
                     정상 처리 진행   409 반환         캐시 응답 반환
                          │           REQUEST_         (동일 202)
                          │           IN_PROGRESS
                          │
                    ┌─────┴─────┐
                    ▼           ▼
                  성공         실패
                    │           │
              complete()   release()
              (응답 캐싱)   (키 삭제→재시도 허용)
```

### 실패 시 키 해제

비즈니스 로직 실패(잔액 부족 등)가 아닌 **시스템 장애**(DB 다운, 타임아웃)로 실패한 경우
키를 삭제하여 클라이언트가 동일 키로 재시도할 수 있도록 한다.

```kotlin
fun withdraw(idempotencyKey: String, request: WithdrawalRequest): TransactionResponse {
    val existing = idempotencyKeyService.tryAcquire(idempotencyKey)
    if (existing != null) {
        return when (existing.status) {
            "COMPLETED" -> objectMapper.readValue(existing.cachedResponse!!)
            else -> throw RequestInProgressException()
        }
    }

    try {
        val response = processWithdrawal(request)   // 분산 락 + 비즈니스 로직
        idempotencyKeyService.complete(idempotencyKey, objectMapper.writeValueAsString(response))
        return response
    } catch (ex: BusinessException) {
        // 비즈니스 에러: 키 유지 (같은 요청 반복해도 같은 에러)
        idempotencyKeyService.complete(idempotencyKey, errorResponse(ex))
        throw ex
    } catch (ex: Exception) {
        // 시스템 장애: 키 해제 (재시도 허용)
        idempotencyKeyService.release(idempotencyKey)
        throw ex
    }
}
```

---

### 보상 트랜잭션

```kotlin
@KafkaListener(topics = ["blockchain.tx.confirmed"])
fun handleBlockConfirmation(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
    val event = parse(record.value(), BlockConfirmedEvent::class.java)

    if (!idempotencyChecker.checkAndMark(event.eventId)) {
        ack.acknowledge()
        return
    }

    val tx = transactionRepository.findByTxHash(event.txHash)

    when (event.status) {
        "MINED" -> {
            tx.status = TransactionStatus.CONFIRMED
        }
        "FAILED" -> {
            tx.status = TransactionStatus.FAILED
            // 보상 트랜잭션: 잔액 복구 이벤트 발행
            val rollbackEvent = BalanceUpdateEvent(
                walletId = tx.walletId,
                amount = tx.amount,
                operation = "CREDIT"
            )
            outboxRepository.save(OutboxEvent.from(rollbackEvent))
        }
    }

    transactionRepository.save(tx)
    ack.acknowledge()
}
```
