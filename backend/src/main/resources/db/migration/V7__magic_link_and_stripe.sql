-- V7__magic_link_and_stripe.sql
-- Replace password auth with magic-link; add Stripe subscription state.

-- ==========================================
-- Users: drop password, add Stripe fields
-- ==========================================
ALTER TABLE users DROP COLUMN passcode;

ALTER TABLE users
    ADD COLUMN stripe_customer_id VARCHAR(64),
    ADD COLUMN stripe_subscription_id VARCHAR(64),
    ADD COLUMN paid_until TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX idx_users_stripe_customer_id
    ON users(stripe_customer_id)
    WHERE stripe_customer_id IS NOT NULL;

CREATE INDEX idx_users_paid_until ON users(paid_until);

-- ==========================================
-- Magic-link tokens
-- One-shot, short-lived tokens for passwordless sign-in. Redeemed tokens are
-- marked used_at instead of deleted, so replay attempts hit a clear signal.
-- ==========================================
CREATE TABLE magic_link_tokens (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_magic_link_tokens_user ON magic_link_tokens(user_id);
CREATE INDEX idx_magic_link_tokens_expires ON magic_link_tokens(expires_at);

-- ==========================================
-- Stripe webhook idempotency
-- One row per event.id Stripe has delivered. Webhook handlers insert here
-- before processing; UNIQUE constraint makes duplicate deliveries a no-op.
-- ==========================================
CREATE TABLE stripe_events (
    id BIGSERIAL PRIMARY KEY,
    stripe_event_id VARCHAR(64) NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stripe_events_type ON stripe_events(event_type);
