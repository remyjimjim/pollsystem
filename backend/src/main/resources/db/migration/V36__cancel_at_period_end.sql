-- V36__cancel_at_period_end.sql
-- Cancelling a membership in the app (decided 2026-10-20): it ends at the end
-- of the month already paid for, not at once. Until then the home page says
-- when it ends and offers "Keep my membership". Set by the app's Cancel /
-- Keep buttons and kept in step with Stripe by the subscription webhooks
-- (a cancellation made in Stripe's own portal shows here too).

ALTER TABLE users ADD COLUMN cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE;
