-- V10__creator_request_processed_by.sql
-- Adds an honest audit trail for who actually decided each creator request.
--
-- `assigned_admin_id` is the admin the routing layer picked at submit time.
-- For stale requests an admin who isn't the original assignee may approve or
-- reject, so the decider needs its own column. This mirrors the same shape
-- admin_requests has had since V5.

ALTER TABLE creator_requests
    ADD COLUMN processed_by_id BIGINT REFERENCES users(id);

CREATE INDEX idx_creator_requests_processed_by
    ON creator_requests(processed_by_id, processed_at DESC);
