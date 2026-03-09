# 7. Implementation Plan

## 7.1 Phase 1: 프로젝트 기반 구축

### 1-1. Gradle 멀티모듈 세팅

| 파일 | 핵심 내용 |
|------|----------|
| `build.gradle.kts` | Kotlin 2.2, Spring Boot 4.0, dependency-management 플러그인 |
| `settings.gradle.kts` | 4개 모듈 include (core, wallet, transaction, blockchain) |
| `core/build.gradle.kts` | 공유 인프라 + 도메인 프리미티브 라이브러리 (JPA, Redis, Kafka, KMS, Jackson 등) |
| `wallet/build.gradle.kts` | Spring Boot 앱, `:core` 의존 |
| `transaction/build.gradle.kts` | Spring Boot 앱, `:core` 의존 |
| `blockchain/build.gradle.kts` | Spring Boot 앱, `:core` 의존 |

### 1-2. Docker Compose

```yaml
services:
  mysql:        # 3306, 3개 DB 자동 생성 (init script)
  redis:        # 6379
  kafka:        # 9092, KRaft mode (Zookeeper 없음)
  localstack:   # 4566, KMS 서비스
```

### 1-3. Core 모듈

`com.ethwallet.core` 패키지 하위에 다음 구성요소를 포함한다.

**domain/** — 공유 도메인 프리미티브
- `Money` Value Object (BigDecimal 래핑, 연산 메서드)
- `EthAddress` Value Object (검증 로직 포함)
- `WalletStatus` enum (ACTIVE, FROZEN, DELETED)
- `TransactionStatus` enum (PENDING, CONFIRMED, FAILED, ROLLBACK)
- `TransactionType` enum (WITHDRAWAL, DEPOSIT)

**event/** — 이벤트 스키마
- `DomainEvent` 추상 클래스 (eventId, eventType, timestamp, aggregateId)
- 5개 이벤트 data class (WalletCreated, WithdrawalRequested, DepositDetected, BlockConfirmed, BalanceUpdate)

**exception/** — 예외 처리
- `ErrorCode` enum (코드, HTTP 상태, 메시지)
- `BusinessException` (ErrorCode 기반)
- `GlobalExceptionHandler` (@RestControllerAdvice)

**jpa/** — JPA 공통 설정
- `BaseEntity` (createdAt, updatedAt, @MappedSuperclass)
- `JpaAuditingConfig` (@EnableJpaAuditing)

**redis/** — Redis 설정
- `RedisConfig` (RedisTemplate, Redisson 설정)
- `DistributedLockService` (Redisson 분산 락)
- `IdempotencyChecker` (Redis SET + TTL)

**kafka/** — Kafka 공통 설정
- `KafkaProducerConfig` (idempotent producer)
- `KafkaConsumerConfig` (manual ACK, DLT error handler)
- `OutboxEvent` 엔티티 + Repository
- `OutboxPollingPublisher` (@Scheduled)

**kms/** — KMS 암호화
- `KmsClient` 인터페이스
- `LocalKmsClient` (로컬 AES mock)
- `EnvelopeEncryptionService` (암/복호화 + DEK 캐싱)
- `BlindIndexService` (HMAC-SHA256)
- `EncryptedStringConverter` (JPA AttributeConverter)

> `blockchain`처럼 Redis/KMS가 불필요한 서비스는 `@ConditionalOnProperty`로 비활성화

---

## 7.2 Phase 2: 지갑 생성 (wallet-service)

| 작업 | 상세 |
|------|------|
| Entity | `Wallet` JPA 엔티티 (EncryptedStringConverter 적용) |
| Repository | `WalletRepository` (findByWalletId, findByAddressBlind) |
| Service | `WalletService.createWallet()` - 키 생성, 암호화, Blind Index, 저장, Outbox |
| Controller | `WalletController` - POST/GET 엔드포인트 |
| ETH 키 생성 | web3j를 사용한 키 쌍 생성 (ECKeyPair → address, privateKey) |
| 이벤트 | WalletCreatedEvent → Outbox → Kafka |

### 패키지 구조

```
wallet/src/main/kotlin/com/ethwallet/wallet/
├── WalletApplication.kt
├── controller/
│   └── WalletController.kt
├── service/
│   └── WalletService.kt
├── domain/
│   ├── Wallet.kt              (Entity)
│   └── WalletRepository.kt
├── dto/
│   ├── WalletResponse.kt
│   └── BalanceResponse.kt
├── event/
│   └── WalletEventConsumer.kt  (wallet.balance.update 구독)
└── internal/
    └── InternalWalletController.kt  (서비스 간 API)
```

---

## 7.3 Phase 3: 출금 (transaction-service + blockchain-service)

### transaction-service

| 작업 | 상세 |
|------|------|
| Entity | `Transaction` (OutboxEvent는 core 모듈) |
| 출금 API | POST /api/v1/transactions/withdrawal |
| 분산 락 | Redisson LOCK:wallet:{walletId} |
| 멱등성 키 | `Idempotency-Key` 헤더 + Redis (IdempotencyKeyService) |
| Circuit Breaker | wallet-service 호출 시 Resilience4j |
| Saga | 실패 시 BalanceUpdateEvent(CREDIT) 발행으로 롤백 |
| Consumer | blockchain.tx.confirmed 구독 → 상태 갱신 |

### blockchain-service

| 작업 | 상세 |
|------|------|
| Entity | `Block`, `BlockchainTransaction` |
| Consumer | tx.withdrawal.requested 구독 → PENDING 트랜잭션 생성 |
| 블록 생성 | @Scheduled 5초 간격, PENDING → MINED |
| 이벤트 발행 | blockchain.tx.confirmed (MINED/FAILED) |

### 출금 시퀀스 다이어그램

```
Client → [transaction-service]
                              │
                    ┌─────────┴──────────┐
                    │ 1. 분산 락 획득      │
                    │ 2. wallet-service    │──REST──► [wallet-service]
                    │    잔액 차감 (CB)     │◄────────  잔액 차감 응답
                    │ 3. Transaction 생성  │
                    │ 4. Outbox 저장       │
                    │ 5. 분산 락 해제      │
                    └─────────┬──────────┘
                              │
                    Outbox Poller → Kafka
                              │
                    tx.withdrawal.requested
                              │
                              ▼
                    [blockchain-service]
                    │ 1. PENDING tx 생성
                    │ 2. 블록 생성 시 MINED
                    │ 3. blockchain.tx.confirmed 발행
                              │
                              ▼
                    [transaction-service]
                    │ MINED → tx CONFIRMED
                    │ FAILED → tx FAILED → wallet.balance.update(CREDIT)
                              │
                              ▼
                    [wallet-service]
                    │ 잔액 복구 (CREDIT)
```

---

## 7.4 Phase 4: 입금 시뮬레이션

### 흐름

```
[blockchain-service]
│ 1. simulate-deposit API 호출 (테스트용)
│    또는 스케줄러가 랜덤 입금 생성
│ 2. PENDING tx 생성
│ 3. 블록 포함 시 MINED
│ 4. tx.deposit.detected 이벤트 발행
        │
        ▼
[transaction-service]
│ 1. DEPOSIT 트랜잭션 생성 (CONFIRMED)
│ 2. wallet.balance.update(CREDIT) 이벤트 발행
        │
        ▼
[wallet-service]
│ 1. 잔액 증가
│ 2. Redis 캐시 갱신
```

---

## 7.5 Phase 5: 인프라 마무리

| 작업 | 상세 |
|------|------|
| LocalStack KMS | LocalKmsClient → AwsKmsClient 교체. docker-compose에서 KMS 키 자동 생성 |
| 통합 테스트 | TestContainers 기반. 전체 흐름 (지갑 생성 → 출금 → 컨펌) 테스트 |
| API 문서 | SpringDoc OpenAPI 3.0. 각 서비스에 swagger-ui 활성화 |

---

## 7.6 검증 시나리오

| # | 시나리오 | 검증 포인트 |
|---|---------|-----------|
| 1 | 지갑 생성 | DB에 암호화된 주소 확인, Blind Index로 검색 확인 |
| 2 | 출금 | Kafka 이벤트 흐름 추적, 잔액 차감 확인 |
| 3 | 입금 시뮬레이션 | 자동 잔액 증가 확인 |
| 4 | CB 테스트 | blockchain-service 중단 → OPEN → 복구 → CLOSED |
| 5 | 동시 출금 | 동일 지갑 동시 출금 → 분산 락으로 하나만 성공 |
| 6 | 멱등성 | 같은 이벤트 2번 발행 → 1번만 처리 |
| 7 | 장애 복구 | 서비스 재시작 후 PENDING 트랜잭션 재처리 |
