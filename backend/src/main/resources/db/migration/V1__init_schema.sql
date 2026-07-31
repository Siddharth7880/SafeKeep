-- V1__init_schema.sql
-- SafeKeep initial database schema

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    checkin_interval_days INTEGER NOT NULL DEFAULT 7,
    grace_period_days INTEGER NOT NULL DEFAULT 3,
    next_checkin_deadline TIMESTAMP,
    grace_period_start TIMESTAMP,
    released_at TIMESTAMP,
    last_checkin_at TIMESTAMP,
    encrypted_master_key_salt VARCHAR(512),
    reminder_count INTEGER DEFAULT 0,
    email_notifications_enabled BOOLEAN DEFAULT TRUE,
    sms_notifications_enabled BOOLEAN DEFAULT FALSE,
    phone_number VARCHAR(20),
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE','MISSED_CHECKIN','GRACE_PERIOD','RELEASED','PAUSED'))
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_next_deadline ON users(next_checkin_deadline) WHERE status = 'ACTIVE';
CREATE INDEX idx_users_grace_period ON users(grace_period_start) WHERE status = 'GRACE_PERIOD';

-- ============================================================
-- VAULT ITEMS
-- ============================================================
CREATE TABLE vault_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label VARCHAR(255) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    encrypted_content TEXT NOT NULL,
    encrypted_dek VARCHAR(512) NOT NULL,
    iv VARCHAR(64) NOT NULL,
    dek_iv VARCHAR(64) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    CONSTRAINT chk_content_type CHECK (content_type IN ('TEXT_MESSAGE','CREDENTIALS','DOCUMENT_NOTE','FINAL_INSTRUCTIONS','PERSONAL_MESSAGE'))
);

CREATE INDEX idx_vault_items_user ON vault_items(user_id);
CREATE INDEX idx_vault_items_active ON vault_items(user_id) WHERE is_active = TRUE;

-- ============================================================
-- RECIPIENTS
-- ============================================================
CREATE TABLE recipients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    relationship VARCHAR(100),
    notify_on_release BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    CONSTRAINT uq_user_recipient_email UNIQUE (user_id, email)
);

CREATE INDEX idx_recipients_user ON recipients(user_id);

-- ============================================================
-- VAULT ITEM RECIPIENTS (M2M)
-- ============================================================
CREATE TABLE vault_item_recipients (
    vault_item_id UUID NOT NULL REFERENCES vault_items(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES recipients(id) ON DELETE CASCADE,
    PRIMARY KEY (vault_item_id, recipient_id)
);

-- ============================================================
-- AUDIT LOGS (append-only)
-- ============================================================
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    previous_status VARCHAR(50),
    new_status VARCHAR(50),
    triggered_by VARCHAR(100),
    ip_address VARCHAR(45),
    details VARCHAR(500),
    created_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_audit_user ON audit_logs(user_id);
CREATE INDEX idx_audit_event_type ON audit_logs(event_type);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at DESC);

-- ============================================================
-- RELEASE TOKENS
-- ============================================================
CREATE TABLE release_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(512) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    vault_item_id UUID NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    accessed_at TIMESTAMP,
    is_used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_release_tokens_token ON release_tokens(token);
CREATE INDEX idx_release_tokens_user ON release_tokens(user_id);
