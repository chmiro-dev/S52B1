-- Schema DDL for H2 (PostgreSQL Compatibility Mode)

DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS ledger_entries CASCADE;
DROP TABLE IF EXISTS accounts CASCADE;
DROP TABLE IF EXISTS user_roles CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS outbox CASCADE;

-- -----------------------------------------------------------------------------
-- Users & Roles
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role_name),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- -----------------------------------------------------------------------------
-- Accounts & Ledger
-- -----------------------------------------------------------------------------
CREATE TABLE accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_number VARCHAR(64) NOT NULL UNIQUE,
    account_type VARCHAR(20) NOT NULL, -- CHECKING, SAVINGS, CD
    status VARCHAR(20) NOT NULL,       -- ACTIVE, FROZEN, CLOSED
    balance DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    version BIGINT NOT NULL DEFAULT 0,  -- Optimistic Locking (@Version)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE ledger_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id VARCHAR(64) NOT NULL,
    account_id BIGINT NOT NULL,
    entry_type VARCHAR(10) NOT NULL,    -- DEBIT, CREDIT
    amount DECIMAL(19, 4) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_ledger_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT
);

CREATE INDEX idx_ledger_transaction ON ledger_entries (transaction_id);

-- -----------------------------------------------------------------------------
-- Phase 3: Transactional Outbox
-- -----------------------------------------------------------------------------
CREATE TABLE outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,   -- e.g., 'LEDGER_TRANSACTION', 'ACCOUNT'
    aggregate_id VARCHAR(64) NOT NULL,     -- e.g., transaction_id or account_number
    event_type VARCHAR(100) NOT NULL,       -- e.g., 'TRANSACTION_POSTED', 'ACCOUNT_FROZEN'
    payload TEXT NOT NULL,                  -- JSON serialized payload
    status VARCHAR(20) NOT NULL,            -- PENDING, PROCESSED, FAILED
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_status_created ON outbox (status, created_at);

-- -----------------------------------------------------------------------------
-- Audit & Compliance
-- -----------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_principal VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    details TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);