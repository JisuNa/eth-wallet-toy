# 1. Architecture Overview

## 1.1 프로젝트 개요

토스 블록체인 백엔드 개발자 채용 포트폴리오용 이더리움 지갑 시스템.
분산 시스템, 장애 내성, 보안, 이벤트 기반 아키텍처 역량을 증명하는 것이 목표.

### 기술 스택

| Category | Technology |
|----------|-----------|
| Language | Kotlin 2.2 |
| Framework | Spring Boot 4.0 (Spring Framework 7) |
| Build | Gradle Kotlin DSL (멀티모듈) |
| Database | MySQL 8.0 (서비스별 분리) |
| Cache / Lock | Redis 7 + Redisson |
| Messaging | Apache Kafka (KRaft mode) |
| CDC | Debezium (Outbox Event Router) |
| Encryption | AWS KMS (LocalStack) + AES-256 |
| Resilience | Resilience4j |
| Secret | AWS Secrets Manager (LocalStack) |
| Mock | WireMock (노드 프로바이더 API 모킹) |
| Container | Docker Compose |

---

## 1.2 MSA 서비스 구성

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client (REST)                            │
└──────┬──────────────────────┬──────────────────────┬────────────┘
       │                      │                      │
       ▼                      ▼                      ▼
┌───────────────┐ ┌──────────────────┐ ┌──────────────────┐
│wallet-service │ │ transaction-     │ │ blockchain-      │
│    :8081      │ │ service :8082    │ │ service :8083    │
│  (wallet_db)  │ │ (transaction_db) │ │ (blockchain_db)  │
└───────┬───────┘ └──────┬───────────┘ └────────┬─────────┘
        │                │                      │
        └────────────────┼──────────────────────┘
                         │
                  ┌──────▼──────┐
                  │    Kafka    │
                  │  (KRaft)    │
                  └─────────────┘
```

| Service | 역할 | DB | Port |
|---------|------|-----|------|
| **wallet-service** | 지갑 생성, 잔액 조회, 주소 관리 | MySQL (wallet_db) | 8081 |
| **transaction-service** | 출금/입금 트랜잭션 처리, 상태 관리 | MySQL (transaction_db) | 8082 |
| **blockchain-service** | 노드 프로바이더 게이트웨이 (트랜잭션 브로드캐스트, 컨펌 확인, 입금 감지), 핫월렛 관리 | MySQL (blockchain_db) | 8083 |

---

## 1.3 서비스 간 통신

### 동기 통신 (REST)
- `transaction-service` → `wallet-service`: 잔액 확인/차감 (Circuit Breaker 적용)

### 비동기 통신 (Kafka)

```
wallet-service ──wallet.created──────────────► blockchain-service (입금 모니터링 주소 등록)

transaction-service ──tx.withdrawal.requested──► blockchain-service

blockchain-service ──blockchain.tx.confirmed───► transaction-service
blockchain-service ──tx.deposit.detected───────► transaction-service

transaction-service ──wallet.balance.update────► wallet-service
```

### 외부 통신 (Node Provider)

```
blockchain-service ──JSON-RPC──► Infura (primary)
                   ──REST API──► Octet (failover)

※ 포트폴리오 환경에서는 WireMock으로 노드 프로바이더 API 모킹
```

### Node Provider 추상화

프로바이더별 프로토콜이 다르므로 어댑터 패턴으로 추상화한다.

```
BlockchainClient (interface)
├── InfuraClient    ── JSON-RPC (eth_sendRawTransaction, eth_getTransactionReceipt)
└── OctetClient     ── REST API (자체 API 스펙)
```

Resilience4j Circuit Breaker로 primary(Infura) 장애 시 failover(Octet)로 자동 전환.

---

## 1.4 Gradle 멀티모듈 구조

```
eth-wallet-toy/
├── build.gradle.kts                     # 루트: 플러그인 선언, 공통 설정
├── settings.gradle.kts                  # 모듈 include
├── gradle.properties                    # Kotlin/Gradle 옵션
│
├── core/                                # 공유 인프라 + 도메인 프리미티브 (라이브러리)
│   └── build.gradle.kts
│
├── wallet/                              # 지갑 도메인 (Spring Boot 앱, :8081)
│   └── build.gradle.kts
│
├── transaction/                         # 트랜잭션 도메인 (Spring Boot 앱, :8082)
│   └── build.gradle.kts
│
├── blockchain/                          # 블록체인 게이트웨이 (Spring Boot 앱, :8083)
│   └── build.gradle.kts
│
└── docker/
    ├── docker-compose.yml               # 인프라 컨테이너
    └── init-db/                         # MySQL 초기화 스크립트
        └── 01-create-databases.sql
```

### 모듈 의존성 그래프

```
         ┌────────┐
         │  core  │  (library jar)
         └───┬────┘
    ┌────────┼────────┐
    ▼        ▼        ▼
 wallet  transaction  blockchain
           │ REST(CB)
           ▼
         wallet
```

- `core`: 공유 도메인 프리미티브(Money, EthAddress VO), 이벤트 스키마, 예외 처리, JPA/Redis/Kafka/KMS 공통 설정
- 도메인 모듈 간 컴파일 의존 없음. `transaction` → `wallet`은 런타임 REST 호출 (Circuit Breaker 적용)
- `blockchain-service`는 핫월렛 키를 Secrets Manager에서 관리. Redis는 `@ConditionalOnProperty`로 비활성화 가능
