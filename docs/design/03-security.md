# 3. Security - KMS Encryption & Blind Index

## 3.1 암호화 전략 개요

| 필드 | 위치 | 암호화 방식 | 목적 |
|------|------|------------|------|
| `private_key` | wallet_db | AES-256-GCM (KMS DEK) | 개인키 절대 보호 |
| `address` | wallet_db | AES-256-GCM (KMS DEK) | 개인정보 보호 |
| `from_address` | transaction_db | AES-256-GCM (KMS DEK) | 거래 주소 보호 |
| `to_address` | transaction_db | AES-256-GCM (KMS DEK) | 거래 주소 보호 |
| `address_blind` | wallet_db | HMAC-SHA256 | 암호화 주소 검색용 |

---

## 3.2 Envelope Encryption

AWS KMS의 Envelope Encryption 패턴을 사용하여 데이터를 암호화한다.

### 흐름

```
┌──────────────────── 암호화 ────────────────────┐
│                                                │
│  1. KMS.GenerateDataKey(CMK)                   │
│     └─► plaintext DEK + encrypted DEK          │
│                                                │
│  2. AES-256-GCM(plaintext DEK, data)           │
│     └─► ciphertext                             │
│                                                │
│  3. 저장: encrypted DEK + ciphertext           │
│     (plaintext DEK는 메모리에서 즉시 삭제)       │
│                                                │
└────────────────────────────────────────────────┘

┌──────────────────── 복호화 ────────────────────┐
│                                                │
│  1. KMS.Decrypt(encrypted DEK)                 │
│     └─► plaintext DEK                          │
│                                                │
│  2. AES-256-GCM.decrypt(plaintext DEK, cipher) │
│     └─► plaintext data                         │
│                                                │
└────────────────────────────────────────────────┘
```

### DEK 캐싱 전략

- 매 암/복호화마다 KMS API를 호출하면 성능 병목
- **DEK 캐시**: 암호화된 DEK를 키로, 복호화된 DEK를 값으로 인메모리 캐시 (Caffeine)
- TTL: 5분, 최대 100개
- 서비스 재시작 시 캐시 소멸 → KMS에서 재복호화

---

## 3.3 KMS 인터페이스 설계

```kotlin
interface KmsClient {
    /** CMK로 새 Data Encryption Key 생성 */
    fun generateDataKey(): DataKeyResult

    /** 암호화된 DEK를 복호화 */
    fun decryptDataKey(encryptedDek: ByteArray): ByteArray
}

data class DataKeyResult(
    val plaintextKey: ByteArray,
    val encryptedKey: ByteArray
)
```

### 구현체

| 구현 | 환경 | 설명 |
|------|------|------|
| `LocalKmsClient` | 개발/테스트 | 로컬 AES 마스터키 사용. properties에서 키 로드 |
| `AwsKmsClient` | Docker (LocalStack) | LocalStack KMS API 호출 |

### EnvelopeEncryptionService

```kotlin
@Service
class EnvelopeEncryptionService(
    private val kmsClient: KmsClient,
    private val dekCache: Cache<String, ByteArray>   // Caffeine
) {
    /** 암호화: ciphertext + encrypted DEK를 Base64로 결합 반환 */
    fun encrypt(plaintext: String): String

    /** 복호화: Base64 파싱 → DEK 복호화 (캐시 우선) → 데이터 복호화 */
    fun decrypt(ciphertext: String): String
}
```

### 저장 포맷

```
Base64(encrypted_dek_length(4 bytes) + encrypted_dek + iv(12 bytes) + ciphertext + tag(16 bytes))
```

- `encrypted_dek_length`: encrypted DEK의 바이트 길이 (4 bytes, big-endian)
- `encrypted_dek`: KMS로 암호화된 DEK
- `iv`: AES-GCM Initialization Vector (12 bytes)
- `ciphertext + tag`: AES-256-GCM 암호문 + 인증 태그

---

## 3.4 Blind Index

암호화된 필드는 `WHERE address = ?` 검색이 불가능하다.
Blind Index를 사용하여 동등 검색(equality search)을 지원한다.

### 생성 방식

```
blind_index = HMAC-SHA256(plaintext_address, blind_index_key)
            → Hex encoding (64 chars)
```

### BlindIndexService

```kotlin
@Service
class BlindIndexService(
    private val blindIndexKey: ByteArray   // KMS로 관리되는 별도 키
) {
    fun generateIndex(plaintext: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(blindIndexKey, "HmacSHA256"))
        return mac.doFinal(plaintext.toByteArray()).toHexString()
    }
}
```

### 검색 흐름

```
1. 클라이언트 → "0x1234...abcd" 주소로 지갑 조회 요청
2. BlindIndexService.generateIndex("0x1234...abcd") → "a8f2e1..."
3. SELECT * FROM wallet WHERE address_blind = 'a8f2e1...'
4. 결과의 address를 KMS로 복호화하여 응답
```

### Blind Index 키 관리

- KMS에서 별도 CMK로 관리
- `LocalKmsClient` 환경에서는 application.yml에 설정
- 키 로테이션 시: 전체 레코드의 Blind Index 재생성 필요 (배치 마이그레이션)

---

## 3.5 JPA AttributeConverter

암호화/복호화를 Entity 레벨에서 투명하게 처리한다.

```kotlin
@Converter
class EncryptedStringConverter(
    private val encryptionService: EnvelopeEncryptionService
) : AttributeConverter<String, String> {

    override fun convertToDatabaseColumn(attribute: String?): String? =
        attribute?.let { encryptionService.encrypt(it) }

    override fun convertToEntityAttribute(dbData: String?): String? =
        dbData?.let { encryptionService.decrypt(it) }
}
```

> **주의**: Spring-managed Converter를 JPA에 등록하려면 `@Converter(autoApply = false)`로 선언하고,
> Entity 필드에 `@Convert(converter = EncryptedStringConverter::class)` 명시적 적용.
