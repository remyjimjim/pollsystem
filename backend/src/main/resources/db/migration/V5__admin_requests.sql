-- V5__admin_requests.sql
-- Admin promotion request flow. Mirrors creator_requests but routes to Supers.

CREATE TABLE admin_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    status request_status NOT NULL DEFAULT 'PENDING',
    reason TEXT NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE,
    processed_by_id BIGINT REFERENCES users(id)
);

CREATE INDEX idx_admin_requests_status ON admin_requests(status);
CREATE INDEX idx_admin_requests_user ON admin_requests(user_id);

ALTER TABLE role_assignments
    ADD COLUMN admin_request_id BIGINT REFERENCES admin_requests(id);

CREATE INDEX idx_role_assignments_admin_request
    ON role_assignments(admin_request_id);
