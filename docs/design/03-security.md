# 3. Security - KMS Encryption & Blind Index

## 3.1 암호화 전략 개요

| 필드 | 위치 | 암호화 방식 | 목적 |
|------|------|------------|------|
| `private_key` | wallet_db | AES-256-GCM (KMS DEK) | 개인키 보호 |
| `address` | wallet_db | AES-256-GCM (KMS DEK) | 개인정보 보호 |
| `from_address` | transaction_db | AES-256-GCM (KMS DEK) | 거래 주소 보호 |
| `to_address` | transaction_db | AES-256-GCM (KMS DEK) | 거래 주소 보호 |
| `address_blind` | wallet_db | HMAC-SHA256 | 암호화 주소 검색용 |

---

## 3.2 Envelope Encryption

AWS KMS Envelope Encryption 패턴.

```
암호화: KMS.GenerateDataKey(CMK) → plaintext DEK + encrypted DEK
       → AES-256-GCM(plaintext DEK, data) → ciphertext
       → 저장: encrypted DEK + ciphertext (plaintext DEK 즉시 삭제)

복호화: KMS.Decrypt(encrypted DEK) → plaintext DEK
       → AES-256-GCM.decrypt(plaintext DEK, cipher) → plaintext
```

- DEK 캐시: Caffeine 인메모리 캐시 (TTL 5분, 최대 100개)
- 서비스 재시작 시 캐시 소멸 → KMS에서 재복호화

---

## 3.3 KMS 구현체

| 구현 | 환경 | 설명 |
|------|------|------|
| `LocalKmsClient` | 개발/테스트 | 로컬 AES 마스터키 사용 |
| `AwsKmsClient` | Docker (LocalStack) | LocalStack KMS API 호출 |

---

## 3.4 Blind Index

암호화된 필드는 `WHERE address = ?` 검색 불가. HMAC-SHA256 Blind Index로 동등 검색 지원.

```
blind_index = HMAC-SHA256(plaintext_address, blind_index_key) → Hex (64 chars)
```

- Blind Index 키는 KMS에서 별도 CMK로 관리
- 키 로테이션 시 전체 레코드 Blind Index 재생성 필요 (배치 마이그레이션)

---

## 3.5 JPA AttributeConverter

`EncryptedStringConverter`로 Entity 레벨에서 암/복호화 투명 처리.

- `@Converter(autoApply = false)` 선언
- Entity 필드에 `@Convert(converter = EncryptedStringConverter::class)` 명시 적용

---

## 3.6 핫월렛 키 관리 (Secrets Manager)

핫월렛 프라이빗 키는 DB에 저장하지 않고 AWS Secrets Manager에서 관리.

- Secret Name: `eth-wallet/hot-wallet` (address, privateKey)
- 매 서명 시 Secrets Manager에서 조회, 메모리 캐싱 없음, 사용 후 즉시 참조 해제
- LocalStack Secrets Manager로 로컬 환경 모킹

### KMS와의 역할 분리

| 대상 | 관리 방식 | 사용 서비스 |
|------|----------|------------|
| 사용자 지갑 private_key, address | KMS Envelope Encryption | wallet-service |
| 트랜잭션 from_address, to_address | KMS Envelope Encryption | transaction-service |
| 핫월렛 private_key | Secrets Manager | blockchain-service |
