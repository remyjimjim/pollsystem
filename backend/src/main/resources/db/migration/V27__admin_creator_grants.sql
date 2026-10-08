-- V27__admin_creator_grants.sql
-- Access is additive: an admin is also a creator across their admin area.
-- Until now that was implicit (CreatorGrantGuard accepted an enabled ADMIN
-- grant as creator coverage), so an admin had no creator grants to manage or
-- switch off. From now on poll creation counts CREATOR grants only, and every
-- admin gets a CREATOR grant mirroring each enabled ADMIN grant: same scope
-- and region, every poll type, enabled, not tied to a creator request.
--
-- Only adds rows: no existing access is removed or widened. A region where
-- the user already has any CREATOR grant (enabled or not, any poll type) is
-- left alone, so a deliberate disable isn't undone.

INSERT INTO role_assignments (user_id, role, scope_level, state_id, county_id, zipcode, poll_type_id, enabled)
SELECT a.user_id, 'CREATOR', a.scope_level, a.state_id, a.county_id, a.zipcode, NULL, TRUE
FROM role_assignments a
WHERE a.role = 'ADMIN'
  AND a.enabled
  AND NOT EXISTS (
    SELECT 1 FROM role_assignments c
    WHERE c.user_id = a.user_id
      AND c.role = 'CREATOR'
      AND c.scope_level = a.scope_level
      AND c.state_id IS NOT DISTINCT FROM a.state_id
      AND c.county_id IS NOT DISTINCT FROM a.county_id
      AND c.zipcode IS NOT DISTINCT FROM a.zipcode
  );
