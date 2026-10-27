-- V41__magic_link_code.sql
-- A 6-digit sign-in code in each sign-in email (decided 2026-10-27): on a phone
-- the email app opens links in its own browser, so the link signs in that
-- browser, not the one the person started in. Typing the code there works
-- anywhere. Stored hashed; same 15-minute life and single use as the link;
-- 5 wrong tries retire the code (the link keeps working).
ALTER TABLE magic_link_tokens
    ADD COLUMN code_hash VARCHAR(64),
    ADD COLUMN code_attempts INT NOT NULL DEFAULT 0;
