-- V43__substack_stripe.sql
-- Paid Substack subscribers become BallotBox members (decided 2026-10-28):
-- Substack bills through its own, separate Stripe account (norfstar.substack),
-- whose webhook tells BallotBox when someone pays, renews or cancels.
-- users.substack_customer_id links a member to that account's customer, so a
-- cancellation (which carries no email) finds them, and marks their membership
-- as coming from Substack. substack_sync_log keeps what each event or re-sync
-- did, for the Super page.
ALTER TABLE users ADD COLUMN substack_customer_id VARCHAR(64);
CREATE INDEX idx_users_substack_customer ON users(substack_customer_id);

CREATE TABLE substack_sync_log (
    id      BIGSERIAL PRIMARY KEY,
    at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source  VARCHAR(30) NOT NULL,      -- webhook | resync | resync-scheduled
    email   VARCHAR(255),
    action  VARCHAR(40) NOT NULL,      -- activated | renewed | ended | ignored | error | summary
    ok      BOOLEAN NOT NULL,
    message TEXT
);
CREATE INDEX idx_substack_sync_log_at ON substack_sync_log(at DESC);
