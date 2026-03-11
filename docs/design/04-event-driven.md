# 4. Event-Driven Architecture

## 4.1 Kafka 토픽 설계

| Topic | Producer | Consumer | 용도 |
|-------|----------|----------|------|
| `wallet.created` | wallet-service | blockchain-service | 입금 모니터링 주소 등록 |
| `tx.withdrawal.requested` | transaction-service | blockchain-service | 출금 요청 → 핫월렛 브로드캐스트 |
| `tx.deposit.detected` | blockchain-service | transaction-service | 입금 감지 알림 |
| `blockchain.tx.confirmed` | blockchain-service | transaction-service | 블록 컨펌/실패 알림 |
| `blockchain.tx.check-confirmation` | blockchain-service | blockchain-service | 브로드캐스트 후 컨펌 확인 (self-publishing) |
| `wallet.balance.update` | transaction-service | wallet-service | 잔액 갱신 (CREDIT/DEBIT) |

---

## 4.2 이벤트 스키마 (core)

이벤트별 전용 테이블을 사용하므로, 각 이벤트는 JPA 엔티티로 정의하며 해당 서비스 모듈에 위치한다.
core에 공통 추상 클래스나 이벤트 스키마를 두지 않는다.

| 이벤트 테이블 (엔티티) | 서비스 | 주요 컬럼 |
|----------------------|--------|----------|
| `WalletCreatedEvent` | wallet-service | eventId, walletId, addressBlind, createdAt |
| `WithdrawalRequestedEvent` | transaction-service | eventId, txId, toAddress, amount, createdAt |
| `DepositDetectedEvent` | blockchain-service | eventId, txHash, toAddress, amount, blockNumber, createdAt |
| `CheckConfirmationEvent` | blockchain-service | eventId, txHash, txId, retryCount, createdAt |
| `BlockConfirmedEvent` | blockchain-service | eventId, txHash, txId, status, createdAt |
| `BalanceUpdateEvent` | transaction-service | eventId, walletId, amount, operation, createdAt |

- CDC가 캡처하므로 별도 직렬화 불필요 (Debezium이 컬럼 값을 JSON으로 변환)

---

## 4.3 이벤트별 전용 테이블 + Debezium CDC

비즈니스 로직과 이벤트 저장을 같은 DB 트랜잭션으로 처리하여 이중 쓰기 문제 방지.
이벤트별 전용 테이블의 INSERT를 Debezium CDC가 캡처하여 Kafka에 자동 발행한다.

### 원본 테이블 CDC 대신 전용 이벤트 테이블을 사용하는 이유

1. **암호화 필드** — 원본 테이블의 암호화 컬럼(EncryptedStringConverter)을 CDC가 캡처하면 ciphertext가 전달됨. 컨슈머가 다른 서비스의 KMS 키에 접근해야 하는 결합 발생.
2. **이벤트 라우팅** — 원본 테이블에서 동일 이벤트가 INSERT/UPDATE 두 경로로 발생 (자동승인 INSERT, 관리자승인 UPDATE). 전용 테이블은 INSERT만 감지하면 됨.
3. **스키마 독립** — 엔티티 컬럼 변경이 컨슈머에 영향을 주지 않음.

### 이벤트 테이블 매핑

| 서비스 | 테이블 | → Kafka 토픽 |
|--------|--------|-------------|
| wallet-service | `wallet_created_event` | `wallet.created` |
| transaction-service | `withdrawal_requested_event` | `tx.withdrawal.requested` |
| transaction-service | `balance_update_event` | `wallet.balance.update` |
| blockchain-service | `check_confirmation_event` | `blockchain.tx.check-confirmation` |
| blockchain-service | `block_confirmed_event` | `blockchain.tx.confirmed` |

### 흐름

```
1. 비즈니스 로직 + 이벤트 테이블 INSERT → 같은 DB 트랜잭션
2. Debezium CDC가 이벤트 테이블 INSERT 감지 → 매핑된 Kafka 토픽으로 자동 발행
```

### Debezium 설정

- Connector: `io.debezium.connector.mysql.MySqlConnector`
- 테이블 단위로 Kafka 토픽 매핑 (테이블명 → 토픽명)
- 서비스별 Debezium Connector 구성 (wallet_db, transaction_db, blockchain_db)
- INSERT만 캡처 (`op = 'c'`)
- 발행 완료 레코드는 주기적으로 삭제 (일별 배치)

---

## 4.4 멱등성 보장 (Consumer)

Kafka at-least-once 특성상 중복 수신 가능. Consumer에서 Redis 기반 멱등성 체크.

- 키: `idempotency:{eventId}`, TTL 24시간
- `setIfAbsent`로 첫 처리 여부 판단
- 이미 처리된 이벤트는 ACK만 보내고 스킵
