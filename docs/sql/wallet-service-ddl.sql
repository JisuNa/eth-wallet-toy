USE wallet;

CREATE TABLE wallet
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id    BIGINT UNSIGNED NOT NULL,
    symbol     VARCHAR(10)     NOT NULL,
    encrypted_address     TEXT            NOT NULL,
    address_blind_index   VARCHAR(64)     NOT NULL,
    encrypted_private_key TEXT            NOT NULL,
    balance    DECIMAL(38,18) UNSIGNED NOT NULL DEFAULT 0,
    status     VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6)     NOT NULL,
    updated_at DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_address_blind_index (address_blind_index),
    UNIQUE KEY uk_wallet_member_symbol (member_id, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ledger
(
    id             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    wallet_id      BIGINT UNSIGNED  NOT NULL,
    transaction_id BIGINT UNSIGNED  NOT NULL,
    type           VARCHAR(20)      NOT NULL,
    amount         DECIMAL(38,18) UNSIGNED NOT NULL,
    balance_before DECIMAL(38,18) UNSIGNED NOT NULL,
    balance_after  DECIMAL(38,18) UNSIGNED NOT NULL,
    created_at     DATETIME(6)      NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_ledger_wallet_id (wallet_id),
    INDEX idx_ledger_transaction_id (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
