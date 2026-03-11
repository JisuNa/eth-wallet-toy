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
  localstack:   # 4566, KMS + Secrets Manager
```

### 1-3. Core 모듈

`com.ethwallet.core` 패키지 하위에 다음 구성요소를 포함한다.

**domain/** — 공유 도메인 프리미티브
- `Money` Value Object (BigDecimal 래핑, 연산 메서드)
- `EthAddress` Value Object (검증 로직 포함)
- `WalletStatus` enum (ACTIVE, FROZEN, DELETED)
- `TransactionStatus` enum (PENDING_APPROVAL, PENDING, CONFIRMED, REJECTED, FAILED, ROLLBACK)
- `TransactionType` enum (WITHDRAWAL, DEPOSIT)

**event/** — 이벤트 스키마
- `DomainEvent` 추상 클래스 (eventId, eventType, timestamp, aggregateId)
- 이벤트 data class (WalletCreated, WithdrawalRequested, DepositDetected, BlockConfirmed, BalanceUpdate)

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

> Redis/KMS가 불필요한 서비스는 `@ConditionalOnProperty`로 비활성화

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
| Internal API | 잔액 차감/증가 API (서비스 간 통신용) |

> 사용자 지갑은 입금 주소 역할. 잔액은 장부(ledger) 기록.
> 출금은 핫월렛에서 처리되며, 사용자 지갑에서 직접 출금하지 않는다.
> 사용자 지갑 → 핫월렛 sweep은 실제 운영에 필요하나 이 프로젝트에서는 구현하지 않는다.

---

## 7.3 Phase 3: 출금 (transaction-service + blockchain-service)

### transaction-service

| 작업 | 상세 |
|------|------|
| Entity | `Transaction` (PENDING_APPROVAL, PENDING, CONFIRMED, REJECTED, FAILED, ROLLBACK) |
| 출금 API | POST /api/v1/transactions/withdrawal |
| 관리자 승인/거부 API | POST /api/v1/admin/transactions/{txId}/approve, reject |
| 시세 체크 | ETH 수량 × 시세(임시 하드코딩) ≥ 50만원 → PENDING_APPROVAL |
| 분산 락 | Redisson LOCK:wallet:{walletId} |
| 멱등성 키 | `Idempotency-Key` 헤더 + Redis |
| Circuit Breaker | wallet-service 호출 시 Resilience4j |
| Saga | 실패/거부 시 BalanceUpdateEvent(CREDIT) 발행으로 잔액 복구 |
| Consumer | blockchain.tx.confirmed 구독 → 상태 갱신 |

### 출금 흐름

```
Client → [transaction-service]
  │ 1. 멱등성 키 확인
  │ 2. 분산 락 획득 (LOCK:wallet:{walletId})
  │ 3. wallet-service 호출 → 잔액 차감 (Circuit Breaker)
  │ 4. 시세 체크: amount × ETH 시세
  │
  ├─ < 50만원 → Transaction PENDING → Outbox: tx.withdrawal.requested
  │
  └─ ≥ 50만원 → Transaction PENDING_APPROVAL
                  │
                  ▼
              관리자 승인 API → PENDING → Outbox: tx.withdrawal.requested
              관리자 거부 API → REJECTED → wallet.balance.update(CREDIT) → 잔액 복구
```

### blockchain-service

| 작업 | 상세 |
|------|------|
| Entity | `BroadcastTransaction` (PENDING, BROADCAST, CONFIRMED, FAILED) |
| Consumer | tx.withdrawal.requested 구독 → 핫월렛으로 서명 → 브로드캐스트 |
| 컨펌 폴링 | @Scheduled로 BROADCAST 상태 트랜잭션 컨펌 확인 |
| 입금 감지 | @Scheduled로 블록 스캔, 우리 주소 입금 감지 (wallet.created 구독으로 주소 목록 관리) |
| 이벤트 발행 | blockchain.tx.confirmed (CONFIRMED/FAILED) |
| 핫월렛 | Secrets Manager에서 키 조회, 매 서명 시 조회 후 즉시 참조 해제 |
| Node Provider | BlockchainClient 인터페이스 → InfuraClient(JSON-RPC), OctetClient(REST API) |
| Failover | Infura CB OPEN → Octet 자동 전환 |

### 브로드캐스트 흐름

```
Kafka [tx.withdrawal.requested]
  │
  ▼
[blockchain-service]
  │ 1. 이벤트 수신 → BroadcastTransaction PENDING 생성
  │ 2. Secrets Manager에서 핫월렛 키 조회
  │ 3. 트랜잭션 서명 (web3j)
  │ 4. 노드 프로바이더로 브로드캐스트 (Infura → 실패 시 Octet failover)
  │ 5. BroadcastTransaction BROADCAST + tx hash 저장
  │ 6. 컨펌 폴링 (@Scheduled) → CONFIRMED/FAILED
  │ 7. Outbox: blockchain.tx.confirmed
  │
  ▼
Kafka [blockchain.tx.confirmed]
  │
  ▼
[transaction-service]
  │ CONFIRMED → Transaction CONFIRMED
  │ FAILED    → Transaction FAILED → wallet.balance.update(CREDIT) → 잔액 복구
```

> 포트폴리오 환경: WireMock으로 Infura/Octet API 모킹

---

## 7.4 Phase 4: 입금 감지

```
[blockchain-service]
  │ 1. @Scheduled 블록 스캔 (노드 프로바이더 API)
  │ 2. 우리 주소로 들어온 입금 감지
  │ 3. Outbox: tx.deposit.detected
  │
  ▼
[transaction-service]
  │ 1. DEPOSIT 트랜잭션 생성 (CONFIRMED)
  │ 2. Outbox: wallet.balance.update(CREDIT)
  │
  ▼
[wallet-service]
  │ 1. 장부 잔액 증가
  │ 2. Redis 캐시 갱신
```

---

## 7.5 Phase 5: 인프라 마무리

| 작업 | 상세 |
|------|------|
| LocalStack | KMS + Secrets Manager. docker-compose에서 키/시크릿 자동 생성 |
| WireMock | Infura JSON-RPC, Octet REST API 모킹 |
| 통합 테스트 | TestContainers 기반. 전체 흐름 (지갑 생성 → 출금 → 컨펌) 테스트 |
| API 문서 | SpringDoc OpenAPI 3.0 |

---

## 7.6 검증 시나리오

| # | 시나리오 | 검증 포인트 |
|---|---------|-----------|
| 1 | 지갑 생성 | DB에 암호화된 주소 확인, Blind Index로 검색 확인 |
| 2 | 출금 (< 50만원) | 즉시 PENDING, Kafka 이벤트 흐름, 핫월렛 브로드캐스트, 잔액 차감 |
| 3 | 출금 (≥ 50만원) | PENDING_APPROVAL → 관리자 승인 → PENDING → 브로드캐스트 |
| 4 | 출금 거부 | PENDING_APPROVAL → REJECTED, 잔액 복구 |
| 5 | 입금 감지 | 블록 스캔 → 자동 잔액 증가 |
| 6 | Node Provider failover | Infura 장애 → Octet 전환 → 정상 브로드캐스트 |
| 7 | 동시 출금 | 동일 지갑 동시 출금 → 분산 락으로 하나만 성공 |
| 8 | 멱등성 | 같은 Idempotency-Key 재요청 → 캐시 응답 반환 |
| 9 | Saga 롤백 | 브로드캐스트 실패 → FAILED → 잔액 복구 → ROLLBACK |
