-- V28__seed_local_admin_statewide.sql (local seed)
-- Creator and admin access is now granted statewide or nationwide only, so the
-- local seed admin becomes a California admin (it was three LA zipcodes, V11),
-- with the matching California creator grant (see V27). Existing finer grants
-- elsewhere are kept; this only changes the dev seed account.

DELETE FROM role_assignments ra
USING users u
WHERE ra.user_id = u.id
  AND u.email = 'admin@local.test'
  AND ra.role IN ('ADMIN', 'CREATOR')
  AND ra.scope_level = 'ZIP'
  AND ra.creator_request_id IS NULL
  AND ra.admin_request_id IS NULL;

INSERT INTO role_assignments (user_id, role, scope_level, state_id, enabled)
SELECT u.id, r.role::access_level, 'STATE', s.id, TRUE
FROM users u
JOIN states s ON s.initial = 'CA'
CROSS JOIN (VALUES ('ADMIN'), ('CREATOR')) AS r(role)
WHERE u.email = 'admin@local.test'
  AND NOT EXISTS (
    SELECT 1 FROM role_assignments ra
    WHERE ra.user_id = u.id AND ra.role = r.role::access_level
      AND ra.scope_level = 'STATE' AND ra.state_id = s.id
  );
