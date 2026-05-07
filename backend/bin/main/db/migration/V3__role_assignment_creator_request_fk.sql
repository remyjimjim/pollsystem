-- V3__role_assignment_creator_request_fk.sql
-- Link RoleAssignment rows back to the CreatorRequest that created them,
-- so approval/rejection can flip the correct rows.

ALTER TABLE role_assignments
    ADD COLUMN creator_request_id BIGINT REFERENCES creator_requests(id);

CREATE INDEX idx_role_assignments_creator_request
    ON role_assignments(creator_request_id);
