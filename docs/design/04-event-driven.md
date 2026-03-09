# 4. Event-Driven Architecture

## 4.1 Kafka 토픽 설계

| Topic | Producer | Consumer | Payload | 용도 |
|-------|----------|----------|---------|------|
| `wallet.created` | wallet-service | transaction-service | WalletCreatedEvent | 지갑 생성 알림 |
| `tx.withdrawal.requested` | transaction-service | blockchain-service | WithdrawalRequestedEvent | 출금 요청 브로드캐스트 |
| `tx.deposit.detected` | blockchain-service | transaction-service | DepositDetectedEvent | 입금 감지 알림 |
| `blockchain.tx.confirmed` | blockchain-service | transaction-service | BlockConfirmedEvent | 블록 컨펌 알림 |
| `wallet.balance.update` | transaction-service | wallet-service | BalanceUpdateEvent | 잔액 갱신 요청 |

### 토픽 설정

| 설정 | 값 | 이유 |
|------|-----|------|
| Partitions | 3 | 서비스당 1 consumer, 적정 병렬성 |
| Replication Factor | 1 | 단일 브로커 (포트폴리오 환경) |
| Retention | 7일 | 디버깅/재처리 여유 |
| Cleanup Policy | delete | 기본값 |

---

## 4.2 이벤트 스키마 (core)

### Base Event

```kotlin
abstract class DomainEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val aggregateId: String
)
```

### 이벤트 정의

```kotlin
// 지갑 생성 완료
data class WalletCreatedEvent(
    val walletId: String,
    val addressBlind: String
) : DomainEvent(
    eventType = "WALLET_CREATED",
    aggregateId = walletId
)

// 출금 요청
data class WithdrawalRequestedEvent(
    val txId: String,
    val fromAddress: String,      // 평문 주소 (서비스 간 내부 통신)
    val toAddress: String,        // 평문 주소
    val amount: BigDecimal
) : DomainEvent(
    eventType = "WITHDRAWAL_REQUESTED",
    aggregateId = txId
)

// 입금 감지
data class DepositDetectedEvent(
    val txHash: String,
    val toAddress: String,        // 평문 주소
    val amount: BigDecimal,
    val blockNumber: Long
) : DomainEvent(
    eventType = "DEPOSIT_DETECTED",
    aggregateId = txHash
)

// 블록 컨펌
data class BlockConfirmedEvent(
    val txHash: String,
    val blockNumber: Long,
    val status: String            // "MINED" or "FAILED"
) : DomainEvent(
    eventType = "BLOCK_CONFIRMED",
    aggregateId = txHash
)

// 잔액 갱신
data class BalanceUpdateEvent(
    val walletId: String,
    val amount: BigDecimal,
    val operation: String         // "CREDIT" or "DEBIT"
) : DomainEvent(
    eventType = "BALANCE_UPDATE",
    aggregateId = walletId
)
```

### 직렬화

- **Jackson JSON** 사용 (Avro 대신 선택)
  - 이유: 스키마 레지스트리 없이 간편, 포트폴리오 수준에서 충분
  - data class의 자동 직렬화/역직렬화 활용
- `@JsonTypeInfo` + `@JsonSubTypes`로 다형성 역직렬화 지원

---

## 4.3 Transactional Outbox Pattern

### 왜 Outbox인가?

```
// 문제: 이중 쓰기 (Dual Write)
@Transactional
fun withdraw(request: WithdrawalRequest) {
    transactionRepository.save(tx)      // 1. DB 저장 ✅
    kafkaTemplate.send(event)           // 2. Kafka 발행 ❌ (실패하면?)
}
// → DB는 커밋됐지만 Kafka 이벤트 유실 가능
```

```
// 해결: Outbox Pattern
@Transactional
fun withdraw(request: WithdrawalRequest) {
    transactionRepository.save(tx)          // 1. DB 저장
    outboxRepository.save(outboxEvent)      // 2. 같은 트랜잭션에 Outbox 저장
}
// → 원자적 보장. 별도 폴러가 Outbox → Kafka 발행
```

### Outbox Polling Publisher

```kotlin
@Component
class OutboxPollingPublisher(
    private val outboxRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    @Scheduled(fixedDelay = 1000)   // 1초 간격 폴링
    @Transactional
    fun publishPendingEvents() {
        val events = outboxRepository
            .findTop100ByPublishedFalseOrderByCreatedAtAsc()

        events.forEach { event ->
            val topic = resolveTopicName(event.eventType)
            kafkaTemplate.send(topic, event.aggregateId, event.payload)
            event.published = true
        }
    }

    private fun resolveTopicName(eventType: String): String = when (eventType) {
        "WALLET_CREATED" -> "wallet.created"
        "WITHDRAWAL_REQUESTED" -> "tx.withdrawal.requested"
        "DEPOSIT_DETECTED" -> "tx.deposit.detected"
        "BLOCK_CONFIRMED" -> "blockchain.tx.confirmed"
        "BALANCE_UPDATE" -> "wallet.balance.update"
        else -> throw IllegalArgumentException("Unknown event type: $eventType")
    }
}
```

### 설정

| 파라미터 | 값 | 설명 |
|---------|-----|------|
| 폴링 간격 | 1초 | `fixedDelay = 1000` |
| 배치 크기 | 100 | `findTop100By...` |
| 정렬 | created_at ASC | FIFO 순서 보장 |

### Outbox 클린업

- 발행 완료된 레코드는 주기적으로 삭제 (일별 배치)
- `DELETE FROM outbox_event WHERE published = true AND created_at < NOW() - INTERVAL 3 DAY`

> **향후 개선**: Polling 방식 대신 Debezium CDC 기반 Outbox Relay로 전환하여
> 폴링 지연 제거 및 DB 부하 감소 가능. (Debezium Outbox Event Router 활용)

---

## 4.4 Kafka 설정 (core)

### Producer 설정

```yaml
spring:
  kafka:
    producer:
      bootstrap-servers: localhost:9092
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all                         # 모든 ISR 확인 후 ACK
      properties:
        enable.idempotence: true        # 멱등 프로듀서
        max.in.flight.requests.per.connection: 5
```

### Consumer 설정

```yaml
spring:
  kafka:
    consumer:
      bootstrap-servers: localhost:9092
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false         # 수동 커밋
      properties:
        isolation.level: read_committed
```

### Consumer 에러 핸들링

```
실패 → Retry (exponential backoff, 최대 3회)
     → 3회 실패 → DLT (Dead Letter Topic) 이동
     → DLT 모니터링으로 수동 처리
```

```kotlin
@Bean
fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
    val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
    factory.setCommonErrorHandler(
        DefaultErrorHandler(
            DeadLetterPublishingRecoverer(kafkaTemplate),
            FixedBackOff(1000L, 3L)   // 1초 간격, 3회 재시도
        )
    )
    factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
    return factory
}
```

---

## 4.5 멱등성 보장 (Consumer)

같은 이벤트가 중복 수신될 수 있으므로 (Kafka at-least-once), Consumer 측에서 멱등성을 보장한다.

### Redis 기반 멱등성 체크

```kotlin
@Component
class IdempotencyChecker(
    private val redisTemplate: StringRedisTemplate
) {
    /**
     * 이벤트가 이미 처리되었는지 확인
     * @return true = 첫 처리, false = 중복
     */
    fun checkAndMark(eventId: String): Boolean {
        val key = "idempotency:$eventId"
        val result = redisTemplate.opsForValue()
            .setIfAbsent(key, "1", Duration.ofHours(24))
        return result == true
    }
}
```

### 사용 패턴

```kotlin
@KafkaListener(topics = ["wallet.balance.update"])
fun handleBalanceUpdate(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
    val event = objectMapper.readValue(record.value(), BalanceUpdateEvent::class.java)

    if (!idempotencyChecker.checkAndMark(event.eventId)) {
        ack.acknowledge()   // 이미 처리됨 → ACK만 보내고 스킵
        return
    }

    walletService.updateBalance(event)
    ack.acknowledge()
}
```
