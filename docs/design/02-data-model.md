# 2. Data Model

## 2.1 Database per Service

각 서비스는 독립된 MySQL 데이터베이스를 사용한다.

| Service | Database | 용도 |
|---------|----------|------|
| wallet-service | wallet_db | 지갑, 주소, 잔액 |
| transaction-service | transaction_db | 트랜잭션, Outbox 이벤트 |
| blockchain-service | blockchain_db | 시뮬레이션 블록, 트랜잭션 |

---

## 2.2 wallet_db

### wallet 테이블

```sql
CREATE TABLE wallet (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    wallet_id       VARCHAR(36) UNIQUE NOT NULL,       -- UUID v4
    address         VARCHAR(255) NOT NULL,              -- KMS 암호화된 ETH 주소
    address_blind   VARCHAR(64) NOT NULL,               -- HMAC-SHA256 Blind Index
    private_key     VARCHAR(512) NOT NULL,              -- KMS 암호화된 Private Key
    balance         DECIMAL(36,18) NOT NULL DEFAULT 0,  -- ETH 단위 (소수점 18자리 = Wei 정밀도)
    status          VARCHAR(20) NOT NULL,               -- ACTIVE, FROZEN, DELETED
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    INDEX idx_address_blind (address_blind),
    INDEX idx_status (status)
);
```

#### 필드 상세

| 필드 | 타입 | 설명 |
|------|------|------|
| `wallet_id` | UUID | 외부 식별자. API 응답에 노출 |
| `address` | AES-256 암호문 | 평문 ETH 주소를 KMS DEK로 암호화 |
| `address_blind` | HMAC-SHA256 | 암호화된 주소 검색용 Blind Index |
| `private_key` | AES-256 암호문 | 절대 노출 불가. KMS DEK로 암호화 |
| `balance` | DECIMAL(36,18) | ETH 단위. 1 ETH = 10^18 Wei |
| `status` | ENUM-like | `ACTIVE`: 정상, `FROZEN`: 동결, `DELETED`: 삭제 |

#### 인덱스 전략
- `idx_address_blind`: Blind Index 기반 주소 동등 검색 (WHERE address_blind = ?)
- `idx_status`: 상태별 지갑 목록 조회

---

## 2.3 transaction_db

### transaction 테이블

```sql
CREATE TABLE transaction (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    tx_id           VARCHAR(36) UNIQUE NOT NULL,        -- UUID v4
    wallet_id       VARCHAR(36) NOT NULL,               -- 출금 지갑 ID (Saga 보상용)
    tx_hash         VARCHAR(66),                        -- 0x + 64 hex chars
    from_address    VARCHAR(255),                       -- KMS 암호화 (입금 시 NULL)
    to_address      VARCHAR(255) NOT NULL,              -- KMS 암호화
    amount          DECIMAL(36,18) NOT NULL,
    fee             DECIMAL(36,18) NOT NULL DEFAULT 0,
    type            VARCHAR(20) NOT NULL,               -- WITHDRAWAL, DEPOSIT
    status          VARCHAR(20) NOT NULL,               -- PENDING → CONFIRMED/FAILED/ROLLBACK
    retry_count     INT NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    INDEX idx_status (status),
    INDEX idx_type_status (type, status)
);
```

#### 트랜잭션 상태 머신

```
                  ┌──────────┐
                  │ PENDING  │
                  └────┬─────┘
                       │
            ┌──────────┼──────────┐
            ▼          ▼          ▼
      ┌──────────┐ ┌────────┐ ┌────────┐
      │CONFIRMED │ │ FAILED │ │ROLLBACK│
      └──────────┘ └────────┘ └────────┘
```

- `PENDING`: 트랜잭션 생성됨, 블록체인 처리 대기
- `CONFIRMED`: 블록체인에서 컨펌 완료
- `FAILED`: 블록체인 처리 실패 (잔액 롤백 필요)
- `ROLLBACK`: 잔액 롤백 완료

### outbox_event 테이블 (Transactional Outbox Pattern)

```sql
CREATE TABLE outbox_event (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    aggregate_type  VARCHAR(50) NOT NULL,               -- 'TRANSACTION', 'WALLET'
    aggregate_id    VARCHAR(36) NOT NULL,               -- tx_id 또는 wallet_id
    event_type      VARCHAR(100) NOT NULL,              -- 'WithdrawalRequested' 등
    payload         TEXT NOT NULL,                       -- JSON 직렬화된 이벤트
    published       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6) NOT NULL,
    INDEX idx_published (published, created_at)
);
```

#### Outbox 처리 흐름

```
1. 비즈니스 로직 + outbox INSERT → 같은 DB 트랜잭션
2. OutboxPollingPublisher (스케줄러)
   → published=false 레코드 조회 (LIMIT 100, ORDER BY created_at)
   → Kafka로 발행
   → published=true로 갱신
3. 발행 실패 시 → 다음 폴링 주기에 재시도
```

---

## 2.4 blockchain_db

### block 테이블

```sql
CREATE TABLE block (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    block_number    BIGINT UNIQUE NOT NULL,
    block_hash      VARCHAR(66) NOT NULL,               -- 0x + 64 hex
    parent_hash     VARCHAR(66) NOT NULL,
    timestamp       DATETIME(6) NOT NULL
);
```

### blockchain_transaction 테이블

```sql
CREATE TABLE blockchain_transaction (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    tx_hash         VARCHAR(66) UNIQUE NOT NULL,        -- 0x + 64 hex
    block_number    BIGINT,                             -- NULL이면 아직 미채굴
    from_address    VARCHAR(42) NOT NULL,               -- 평문 (시뮬레이터 내부)
    to_address      VARCHAR(42) NOT NULL,               -- 평문 (시뮬레이터 내부)
    amount          DECIMAL(36,18) NOT NULL,
    status          VARCHAR(20) NOT NULL,               -- PENDING, MINED, FAILED
    created_at      DATETIME(6) NOT NULL,
    FOREIGN KEY (block_number) REFERENCES block(block_number)
);
```

> **Note**: blockchain-service는 시뮬레이터이므로 주소를 암호화하지 않는다.
> 실제 이더리움 네트워크의 주소는 공개 정보이기 때문.

#### 블록 생성 시뮬레이션 흐름

```
1. 스케줄러: 5초 간격으로 블록 생성
2. PENDING 상태 트랜잭션을 블록에 포함 (최대 10개/블록)
3. 포함된 트랜잭션 → MINED 상태로 변경
4. blockchain.tx.confirmed 이벤트 발행
```

---

## 2.5 JPA Entity 설계

### BaseEntity (core)

```kotlin
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @CreatedDate
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    @LastModifiedDate
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
}
```

### Wallet Entity

```kotlin
@Entity
@Table(name = "wallet")
class Wallet(
    @Column(unique = true, nullable = false)
    val walletId: String = UUID.randomUUID().toString(),

    @Convert(converter = EncryptedStringConverter::class)
    @Column(nullable = false)
    var address: String,

    @Column(nullable = false)
    val addressBlind: String,       // HMAC-SHA256 (암호화 X)

    @Convert(converter = EncryptedStringConverter::class)
    @Column(nullable = false)
    var privateKey: String,

    @Column(precision = 36, scale = 18, nullable = false)
    var balance: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: WalletStatus = WalletStatus.ACTIVE,

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
) : BaseEntity()
```

### Transaction Entity

```kotlin
@Entity
@Table(name = "transaction")
class Transaction(
    @Column(unique = true, nullable = false)
    val txId: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    val walletId: String,

    var txHash: String? = null,

    @Convert(converter = EncryptedStringConverter::class)
    var fromAddress: String? = null,

    @Convert(converter = EncryptedStringConverter::class)
    @Column(nullable = false)
    var toAddress: String,

    @Column(precision = 36, scale = 18, nullable = false)
    val amount: BigDecimal,

    @Column(precision = 36, scale = 18, nullable = false)
    var fee: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: TransactionType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TransactionStatus = TransactionStatus.PENDING,

    var retryCount: Int = 0,

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
) : BaseEntity()
```
