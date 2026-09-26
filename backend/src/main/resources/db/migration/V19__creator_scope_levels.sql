-- V19__creator_scope_levels.sql
-- Add coarse scope levels to role_assignments so a creator's purview can be
-- expressed at ZIP / COUNTY / STATE / NATIONAL granularity. A national grant is
-- then a handful of rows instead of ~33k per-zip rows. Existing rows are all
-- ZIP-scoped (the column default backfills them), and the finer geography
-- columns become nullable so a coarser grant can leave them empty:
--   ZIP      → state_id + county_id + zipcode set
--   COUNTY   → state_id + county_id set, zipcode null
--   STATE    → state_id set, county_id + zipcode null
--   NATIONAL → all three null

CREATE TYPE scope_level AS ENUM ('ZIP', 'COUNTY', 'STATE', 'NATIONAL');

ALTER TABLE role_assignments
    ADD COLUMN scope_level scope_level NOT NULL DEFAULT 'ZIP';

ALTER TABLE role_assignments ALTER COLUMN state_id DROP NOT NULL;
ALTER TABLE role_assignments ALTER COLUMN county_id DROP NOT NULL;
ALTER TABLE role_assignments ALTER COLUMN zipcode DROP NOT NULL;

CREATE INDEX idx_role_assignments_role_scope_state
    ON role_assignments(role, scope_level, state_id, enabled);
