-- ============================================================
-- ETH Wallet Toy - Database Initialization
-- 서비스별 독립 데이터베이스 생성 (Database per Service)
-- ============================================================

CREATE DATABASE IF NOT EXISTS wallet_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
