# 6. API Specification

## 6.1 wallet-service API

### POST /api/v1/wallets - 지갑 생성

**Request**

```
POST /api/v1/wallets
Content-Type: application/json
```

Body 없음. (키 쌍은 서버에서 자동 생성)

**Response** `201 Created`

```json
{
  "walletId": "550e8400-e29b-41d4-a716-446655440000",
  "address": "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
  "balance": "0",
  "status": "ACTIVE",
  "createdAt": "2026-03-04T10:30:00"
}
```

**흐름**

```
1. ETH 키 쌍 생성 (시뮬레이션: 랜덤 생성)
2. KMS로 private_key, address 암호화
3. Blind Index 생성 (address → HMAC-SHA256)
4. DB 저장
5. Outbox에 WalletCreatedEvent 저장
6. 응답 반환 (address는 복호화하여 반환)
```

---

### GET /api/v1/wallets/{walletId} - 지갑 조회

**Response** `200 OK`

```json
{
  "walletId": "550e8400-e29b-41d4-a716-446655440000",
  "address": "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
  "balance": "1.500000000000000000",
  "status": "ACTIVE",
  "createdAt": "2026-03-04T10:30:00"
}
```

---

### GET /api/v1/wallets/{walletId}/balance - 잔액 조회

**Response** `200 OK`

```json
{
  "walletId": "550e8400-e29b-41d4-a716-446655440000",
  "balance": "1.500000000000000000"
}
```

> Redis 캐시 fallback 적용. wallet-service DB 장애 시 캐시에서 반환.

---

## 6.2 transaction-service API

### POST /api/v1/transactions/withdrawal - 출금 요청

**Request**

```
POST /api/v1/transactions/withdrawal
Content-Type: application/json
Idempotency-Key: <UUID v4>          ← 필수
```

```json
{
  "walletId": "550e8400-e29b-41d4-a716-446655440000",
  "toAddress": "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B",
  "amount": "0.500000000000000000"
}
```

> **Idempotency-Key**: 클라이언트가 생성한 UUID v4. 동일 키로 재요청 시 최초 응답을 그대로 반환한다.
> 키 유효기간은 24시간이며, 만료 후 같은 키로 요청하면 새 요청으로 처리된다.

**Response** `202 Accepted`

```json
{
  "txId": "660e8400-e29b-41d4-a716-446655440001",
  "status": "PENDING",
  "amount": "0.500000000000000000",
  "fee": "0.001000000000000000",
  "createdAt": "2026-03-04T10:35:00"
}
```

**에러 응답**

| Status | Code | 설명 |
|--------|------|------|
| 400 | `MISSING_IDEMPOTENCY_KEY` | Idempotency-Key 헤더 누락 |
| 400 | `INSUFFICIENT_BALANCE` | 잔액 부족 |
| 404 | `WALLET_NOT_FOUND` | 지갑 없음 |
| 409 | `CONCURRENT_ACCESS` | 동시 출금 시도 (분산 락 실패) |
| 409 | `REQUEST_IN_PROGRESS` | 동일 Idempotency-Key 요청이 처리 중 |
| 503 | `SERVICE_UNAVAILABLE` | Circuit Breaker OPEN |

**흐름**

```
1. Idempotency-Key 헤더 검증 (없으면 400)
2. Redis에서 멱등성 키 조회
   → COMPLETED 상태면 캐시된 응답 즉시 반환
   → PROCESSING 상태면 409 REQUEST_IN_PROGRESS
3. Redis에 PROCESSING 상태로 SET (NX, TTL 24h)
4. 분산 락 획득 (LOCK:wallet:{walletId})
5. wallet-service에 잔액 확인/차감 REST 호출 (Circuit Breaker)
6. Transaction 엔티티 생성 (status=PENDING)
7. OutboxEvent 저장 (WithdrawalRequestedEvent)
8. 분산 락 해제
9. Redis 멱등성 키를 COMPLETED + 응답 본문으로 갱신
10. 202 Accepted 응답
    → 이후 Outbox Poller → Kafka → blockchain-service 비동기 처리
```

---

### GET /api/v1/transactions/{txId} - 트랜잭션 조회

**Response** `200 OK`

```json
{
  "txId": "660e8400-e29b-41d4-a716-446655440001",
  "txHash": "0x3a4b5c6d7e8f...",
  "fromAddress": "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
  "toAddress": "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B",
  "amount": "0.500000000000000000",
  "fee": "0.001000000000000000",
  "type": "WITHDRAWAL",
  "status": "CONFIRMED",
  "createdAt": "2026-03-04T10:35:00"
}
```

---

### GET /api/v1/transactions?walletId={walletId} - 트랜잭션 목록

**Query Parameters**

| Parameter | Required | Default | 설명 |
|-----------|----------|---------|------|
| walletId | Yes | - | 지갑 ID |
| type | No | ALL | WITHDRAWAL, DEPOSIT |
| status | No | ALL | PENDING, CONFIRMED, FAILED, ROLLBACK |
| page | No | 0 | 페이지 번호 |
| size | No | 20 | 페이지 크기 |

**Response** `200 OK`

```json
{
  "content": [
    {
      "txId": "...",
      "type": "WITHDRAWAL",
      "status": "CONFIRMED",
      "amount": "0.500000000000000000",
      "createdAt": "2026-03-04T10:35:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

## 6.3 blockchain-service Internal API

> 내부 서비스 간 통신 전용. 외부 노출 금지.

### POST /internal/v1/blockchain/broadcast - 트랜잭션 브로드캐스트

**Request**

```json
{
  "txHash": "0x3a4b5c6d7e8f...",
  "fromAddress": "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
  "toAddress": "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B",
  "amount": "0.500000000000000000"
}
```

**Response** `202 Accepted`

```json
{
  "txHash": "0x3a4b5c6d7e8f...",
  "status": "PENDING"
}
```

---

### POST /internal/v1/blockchain/simulate-deposit - 입금 시뮬레이션 (테스트용)

**Request**

```json
{
  "toAddress": "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
  "amount": "1.000000000000000000"
}
```

**Response** `202 Accepted`

```json
{
  "txHash": "0x9f8e7d6c5b4a...",
  "status": "PENDING"
}
```

---

### GET /internal/v1/blockchain/blocks/latest - 최신 블록 조회

**Response** `200 OK`

```json
{
  "blockNumber": 42,
  "blockHash": "0xabc123...",
  "parentHash": "0xdef456...",
  "transactionCount": 3,
  "timestamp": "2026-03-04T10:40:00"
}
```

---

## 6.4 Internal API (서비스 간)

### wallet-service 내부 API

```
POST   /internal/v1/wallets/{walletId}/deduct    # 잔액 차감 (transaction-service → wallet-service)
POST   /internal/v1/wallets/{walletId}/credit     # 잔액 증가 (이벤트 기반이지만 REST fallback)
```

**POST /internal/v1/wallets/{walletId}/deduct**

```json
// Request
{ "amount": "0.500000000000000000" }

// Response 200 OK
{ "walletId": "...", "remainingBalance": "1.000000000000000000" }

// Response 400 Bad Request
{ "code": "INSUFFICIENT_BALANCE", "message": "잔액이 부족합니다." }
```

---

## 6.5 공통 에러 응답 형식

```json
{
  "code": "INSUFFICIENT_BALANCE",
  "message": "잔액이 부족합니다.",
  "timestamp": "2026-03-04T10:35:00"
}
```

### 에러 코드 (core)

| Code | HTTP Status | 설명 |
|------|------------|------|
| `WALLET_NOT_FOUND` | 404 | 지갑을 찾을 수 없음 |
| `TRANSACTION_NOT_FOUND` | 404 | 트랜잭션을 찾을 수 없음 |
| `INSUFFICIENT_BALANCE` | 400 | 잔액 부족 |
| `INVALID_ADDRESS` | 400 | 잘못된 ETH 주소 형식 |
| `INVALID_AMOUNT` | 400 | 잘못된 금액 (음수, 0 등) |
| `MISSING_IDEMPOTENCY_KEY` | 400 | Idempotency-Key 헤더 누락 |
| `CONCURRENT_ACCESS` | 409 | 동시 접근 (분산 락 실패) |
| `REQUEST_IN_PROGRESS` | 409 | 동일 멱등성 키 요청 처리 중 |
| `WALLET_FROZEN` | 403 | 동결된 지갑 |
| `SERVICE_UNAVAILABLE` | 503 | 의존 서비스 장애 |
| `INTERNAL_ERROR` | 500 | 내부 서버 에러 |
