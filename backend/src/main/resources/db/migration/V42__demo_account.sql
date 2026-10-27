-- V42__demo_account.sql
-- The demo account (decided 2026-10-28): one shared address per site
-- (app.demo.email) that a super switches on for 12 hours at a time. While on,
-- typing that address on Sign in signs straight in, with no email, code or
-- payment. It's a member and a creator. When it ends (expiry or End now), its
-- polls are archived and handed to the super, and the account is deleted with
-- its votes and comments.
ALTER TABLE users
    ADD COLUMN demo_until TIMESTAMPTZ,
    ADD COLUMN demo_started_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
