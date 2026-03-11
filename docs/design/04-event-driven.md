# 4. Event-Driven Architecture

## 4.1 Kafka 토픽 설계

| Topic | Producer | Consumer | 용도 |
|-------|----------|----------|------|
| `wallet.created` | wallet-service | blockchain-service | 입금 모니터링 주소 등록 |
| `tx.withdrawal.requested` | transaction-service | blockchain-service | 출금 요청 → 핫월렛 브로드캐스트 |
| `tx.deposit.detected` | blockchain-service | transaction-service | 입금 감지 알림 |
| `blockchain.tx.confirmed` | blockchain-service | transaction-service | 블록 컨펌/실패 알림 |
| `wallet.balance.update` | transaction-service | wallet-service | 잔액 갱신 (CREDIT/DEBIT) |

---

## 4.2 이벤트 스키마 (core)

모든 이벤트는 `DomainEvent` 추상 클래스를 상속한다.

**Base**: eventId(UUID), eventType, timestamp, aggregateId

| 이벤트 | 주요 필드 |
|--------|----------|
| WalletCreatedEvent | walletId, addressBlind |
| WithdrawalRequestedEvent | txId, toAddress, amount |
| DepositDetectedEvent | txHash, toAddress, amount, blockNumber |
| BlockConfirmedEvent | txHash, txId, status (CONFIRMED/FAILED) |
| BalanceUpdateEvent | walletId, amount, operation (CREDIT/DEBIT) |

- 직렬화: Jackson JSON
- 다형성 역직렬화: `@JsonTypeInfo` + `@JsonSubTypes`

---

## 4.3 Transactional Outbox Pattern

비즈니스 로직과 이벤트 저장을 같은 DB 트랜잭션으로 처리하여 이중 쓰기 문제 방지.

### 흐름

```
1. 비즈니스 로직 + outbox_event INSERT → 같은 DB 트랜잭션
2. OutboxPollingPublisher (@Scheduled) → published=false 조회 → Kafka 발행 → published=true
3. 발행 실패 시 다음 폴링에서 재시도
```

- 발행 완료 레코드는 주기적으로 삭제 (일별 배치)

---

## 4.4 멱등성 보장 (Consumer)

Kafka at-least-once 특성상 중복 수신 가능. Consumer에서 Redis 기반 멱등성 체크.

- 키: `idempotency:{eventId}`, TTL 24시간
- `setIfAbsent`로 첫 처리 여부 판단
- 이미 처리된 이벤트는 ACK만 보내고 스킵
