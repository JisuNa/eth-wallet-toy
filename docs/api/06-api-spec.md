# 6. API Specification

## 6.1 wallet-service API

### POST /api/v1/wallets — 지갑 생성

Body 없음. 키 쌍은 서버에서 자동 생성.

**Response** `201 Created`

```json
{
  "walletId": "UUID",
  "address": "0x...",
  "balance": "0",
  "status": "ACTIVE",
  "createdAt": "2026-03-04T10:30:00"
}
```

### GET /api/v1/wallets/{walletId} — 지갑 조회

### GET /api/v1/wallets/{walletId}/balance — 잔액 조회

> Redis 캐시 fallback 적용

---

## 6.2 transaction-service API

### POST /api/v1/transactions/withdrawal — 출금 요청

**Headers**: `Idempotency-Key: <UUID>` (필수)

```json
{
  "walletId": "UUID",
  "toAddress": "0x...",
  "amount": "0.500000000000000000"
}
```

**Response** `202 Accepted`

```json
{
  "txId": "UUID",
  "status": "PENDING | PENDING_APPROVAL",
  "amount": "0.500000000000000000",
  "createdAt": "2026-03-04T10:35:00"
}
```

- 시세 기준 50만원 미만: `PENDING` (즉시 출금 프로세스)
- 시세 기준 50만원 이상: `PENDING_APPROVAL` (관리자 승인 대기)
- 시세는 임시 하드코딩 값 사용

**에러 응답**

| Status | Code | 설명 |
|--------|------|------|
| 400 | `MISSING_IDEMPOTENCY_KEY` | Idempotency-Key 헤더 누락 |
| 400 | `INSUFFICIENT_BALANCE` | 잔액 부족 |
| 404 | `WALLET_NOT_FOUND` | 지갑 없음 |
| 409 | `CONCURRENT_ACCESS` | 동시 출금 시도 (분산 락 실패) |
| 409 | `REQUEST_IN_PROGRESS` | 동일 Idempotency-Key 요청이 처리 중 |
| 503 | `SERVICE_UNAVAILABLE` | Circuit Breaker OPEN |

### POST /api/v1/admin/transactions/{txId}/approve — 관리자 승인

**Response** `200 OK`

- PENDING_APPROVAL → PENDING 상태 전이
- 출금 이벤트 발행

### POST /api/v1/admin/transactions/{txId}/reject — 관리자 거부

**Response** `200 OK`

- PENDING_APPROVAL → REJECTED 상태 전이
- 장부 잔액 복구

### GET /api/v1/transactions/{txId} — 트랜잭션 조회

### GET /api/v1/transactions?walletId={walletId} — 트랜잭션 목록

Query: walletId(필수), type, status, page, size

---

## 6.3 Internal API (서비스 간)

> 내부 서비스 간 통신 전용. 외부 노출 금지.

| 경로 | 용도 |
|------|------|
| `POST /internal/v1/wallets/{walletId}/deduct` | 잔액 차감 (transaction → wallet) |
| `POST /internal/v1/wallets/{walletId}/credit` | 잔액 증가 (이벤트 기반이지만 REST fallback) |

---

## 6.4 공통 에러 응답 형식

```json
{
  "code": "ERROR_CODE",
  "message": "에러 메시지",
  "timestamp": "2026-03-04T10:35:00"
}
```

| Code | HTTP Status | 설명 |
|------|------------|------|
| `WALLET_NOT_FOUND` | 404 | 지갑을 찾을 수 없음 |
| `TRANSACTION_NOT_FOUND` | 404 | 트랜잭션을 찾을 수 없음 |
| `INSUFFICIENT_BALANCE` | 400 | 잔액 부족 |
| `INVALID_ADDRESS` | 400 | 잘못된 ETH 주소 형식 |
| `INVALID_AMOUNT` | 400 | 잘못된 금액 |
| `MISSING_IDEMPOTENCY_KEY` | 400 | Idempotency-Key 헤더 누락 |
| `CONCURRENT_ACCESS` | 409 | 동시 접근 (분산 락 실패) |
| `REQUEST_IN_PROGRESS` | 409 | 동일 멱등성 키 요청 처리 중 |
| `WALLET_FROZEN` | 403 | 동결된 지갑 |
| `SERVICE_UNAVAILABLE` | 503 | 의존 서비스 장애 |
| `INTERNAL_ERROR` | 500 | 내부 서버 에러 |
