-- V11__seed_local_admin.sql
-- Seeds a single ADMIN user for local development so the admin dashboard,
-- creator-request review flow, and Super admin-workload table can be
-- exercised without first approving an AdminRequest by hand.
--
-- The seed admin covers the three Los Angeles County zipcodes that V2
-- already populates (90001, 90012, 90210). Sign in via /login using
-- admin@local.test; the magic-link email lands in Mailpit at :8025.
--
-- Idempotent: the user is INSERTed with ON CONFLICT(email) DO NOTHING and
-- the role assignments use NOT EXISTS guards, so re-running this migration
-- (or hand-editing and re-applying V11 against a fresh DB) is safe.

INSERT INTO users (email, phone, zipcode, access, is_enabled)
VALUES ('admin@local.test', '+15550000001', '90001', 'ADMIN', TRUE)
ON CONFLICT (email) DO NOTHING;

-- ADMIN role assignments for LA County zips (state=CA, county=Los Angeles).
INSERT INTO role_assignments (user_id, role, state_id, county_id, zipcode, enabled)
SELECT u.id, 'ADMIN', s.id, c.id, z.zipcode, TRUE
FROM users u
JOIN states s ON s.initial = 'CA'
JOIN counties c ON c.state_id = s.id AND c.name = 'Los Angeles'
JOIN county_zips z ON z.county_id = c.id AND z.zipcode IN ('90001', '90012', '90210')
WHERE u.email = 'admin@local.test'
  AND NOT EXISTS (
    SELECT 1 FROM role_assignments ra
    WHERE ra.user_id = u.id
      AND ra.role = 'ADMIN'
      AND ra.zipcode = z.zipcode
  );
