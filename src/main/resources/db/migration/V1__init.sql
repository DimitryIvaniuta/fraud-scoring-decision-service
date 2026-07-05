CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS fraud_decisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id VARCHAR(128) NOT NULL UNIQUE,
    request_fingerprint CHAR(64) NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    verdict VARCHAR(32) NOT NULL,
    score INTEGER NOT NULL CHECK (score >= 0 AND score <= 100),
    reasons JSONB NOT NULL,
    request_payload JSONB NOT NULL,
    feature_snapshot JSONB NOT NULL,
    feature_source VARCHAR(32) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_fraud_decisions_created_at ON fraud_decisions (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_fraud_decisions_verdict ON fraud_decisions (verdict);

CREATE TABLE IF NOT EXISTS transaction_features (
    customer_id VARCHAR(128) NOT NULL,
    merchant_id VARCHAR(128) NOT NULL,
    average_amount NUMERIC(19, 4) NOT NULL,
    chargeback_rate NUMERIC(8, 6) NOT NULL,
    high_risk_country BOOLEAN NOT NULL,
    account_age_days INTEGER NOT NULL,
    velocity_24h INTEGER NOT NULL,
    prior_declines_24h INTEGER NOT NULL,
    source VARCHAR(64) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (customer_id, merchant_id)
);

CREATE INDEX IF NOT EXISTS idx_transaction_features_refreshed_at ON transaction_features (refreshed_at DESC);

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id VARCHAR(128) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'FAILED', 'DEAD_LETTER', 'PUBLISHED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_pending
    ON outbox_events (status, available_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING', 'FAILED');
