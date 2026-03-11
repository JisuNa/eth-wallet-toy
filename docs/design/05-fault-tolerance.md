# 5. Fault Tolerance

## 5.1 Circuit Breaker (Resilience4j)

| 호출 경로 | Fallback |
|----------|----------|
| transaction-service → wallet-service (잔액 차감) | 즉시 실패 응답 |
| blockchain-service → Infura (primary) | Octet으로 failover |
| blockchain-service → Octet (failover) | 즉시 실패 → 재시도 큐 |

### Node Provider Failover

```
BlockchainClient (interface)
├── InfuraClient    ── JSON-RPC
└── OctetClient     ── REST API

Infura CB OPEN → Octet 자동 전환
Octet CB OPEN  → 즉시 실패, PENDING 유지 후 복구 시 재처리
```

---

## 5.2 분산 락 (Redisson)

동일 지갑에 대한 동시 출금 요청 방지. 잔액 차감의 원자성 보장.

- 락 키: `LOCK:wallet:{walletId}`
- 획득 실패 시 409 CONCURRENT_ACCESS 응답

---

## 5.3 Retry 전략

### Kafka Consumer

- 실패 시 재시도 (최대 3회)
- 3회 초과 시 DLT(Dead Letter Topic)로 이동
- DLT 네이밍: `{원본토픽}.DLT`

### REST 호출

- Resilience4j Retry로 일시적 네트워크 장애 재시도

---

## 5.4 Fallback 전략

| 장애 상황 | Fallback | 복구 |
|----------|----------|------|
| 노드 프로바이더 전체 장애 | 트랜잭션 PENDING 유지 | 복구 후 PENDING 트랜잭션 재처리 |
| wallet-service 잔액 조회 실패 | Redis 캐시에서 마지막 잔액 반환 | 복구 시 캐시 갱신 |
| Kafka 발행 실패 | Outbox 테이블에 남아있음 | 다음 폴링 주기에 재발행 |

---

## 5.5 Saga Pattern (출금 롤백)

Choreography Saga로 블록체인 처리 실패 시 잔액 복구.

### 정상 흐름

```
1. [transaction-service] 잔액 차감 → tx PENDING → Outbox: tx.withdrawal.requested
2. [blockchain-service] 핫월렛으로 서명 → 노드 프로바이더 브로드캐스트 → 컨펌 폴링
3. [blockchain-service] Outbox: blockchain.tx.confirmed (CONFIRMED)
4. [transaction-service] tx CONFIRMED
```

### 실패 & 롤백 흐름

```
1. [transaction-service] 잔액 차감 → tx PENDING → Outbox: tx.withdrawal.requested
2. [blockchain-service] 브로드캐스트 또는 컨펌 실패
3. [blockchain-service] Outbox: blockchain.tx.confirmed (FAILED)
4. [transaction-service] tx FAILED → Outbox: wallet.balance.update (CREDIT)
5. [wallet-service] 잔액 복구
6. [transaction-service] tx ROLLBACK
```

---

## 5.6 REST API 멱등성 (출금)

`Idempotency-Key` 헤더 기반. 분산 락은 동시 요청만 차단하며, 순차 중복 요청은 멱등성 키로 방지.

- Redis에 키 상태 저장: `PROCESSING` → `COMPLETED`
- 동일 키 재요청 시: PROCESSING이면 409, COMPLETED이면 캐시 응답 반환
- 시스템 장애 시 키 해제하여 재시도 허용
- 비즈니스 에러 시 키 유지 (같은 요청 반복해도 같은 에러)
