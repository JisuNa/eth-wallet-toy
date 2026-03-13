-- ============================================================
-- wallet_db - 지갑 서비스 스키마
-- wallet-service(:8081) 전용 데이터베이스
-- ============================================================

USE wallet_db;

-- ------------------------------------------------------------
-- wallet: ETH 지갑 정보
-- address, private_key는 KMS Envelope Encryption(AES-256-GCM)으로 암호화 저장
-- address_blind는 HMAC-SHA256 Blind Index로 암호화된 주소의 동등 검색 지원
-- ------------------------------------------------------------
CREATE TABLE wallet (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    wallet_id       VARCHAR(36)     NOT NULL COMMENT 'UUID v4, 외부 식별자',
    address         VARCHAR(255)    NOT NULL COMMENT 'KMS 암호화된 ETH 주소 (Base64)',
    address_blind   VARCHAR(64)     NOT NULL COMMENT 'HMAC-SHA256 Blind Index (Hex)',
    private_key     VARCHAR(512)    NOT NULL COMMENT 'KMS 암호화된 개인키 (Base64)',
    balance         DECIMAL(36,18)  NOT NULL DEFAULT 0 COMMENT 'ETH 잔액 (소수점 18자리 = Wei 정밀도)',
    status          VARCHAR(20)     NOT NULL COMMENT '지갑 상태: ACTIVE, FROZEN, DELETED',
    created_at      DATETIME(6)     NOT NULL COMMENT '생성 시각',
    updated_at      DATETIME(6)     NOT NULL COMMENT '최종 수정 시각',

    CONSTRAINT uk_wallet_wallet_id UNIQUE (wallet_id),
    INDEX idx_wallet_address_blind (address_blind) COMMENT 'Blind Index 기반 주소 동등 검색',
    INDEX idx_wallet_status (status) COMMENT '상태별 지갑 목록 조회'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='ETH 지갑 - 주소/개인키는 KMS 암호화, Blind Index로 검색';
