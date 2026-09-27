-- V22__poll_purviews.sql
-- A poll's PURVIEW: its territorial scope, used to classify respondents as
-- within/outside purview (results) and to surface polls in zip-based search.
-- Distinct from the admin block / kill-switch features.
--
-- Polymorphic (poll_type, poll_id) like poll_type_blocks / poll_notes, one-to-
-- many rows per poll, reusing the scope_level enum from V19. Ballot measures
-- store NO rows — they inherit their parent election's purview.
--
-- Row shape mirrors role_assignments' coarse-scope convention:
--   ZIP      -> zipcode set (state_id/county_id null)
--   COUNTY   -> county_id set
--   STATE    -> state_id set
--   NATIONAL -> all null

CREATE TABLE poll_purviews (
    id BIGSERIAL PRIMARY KEY,
    poll_type VARCHAR(32) NOT NULL
        CHECK (poll_type IN ('ELECTION','QUESTIONNAIRE','BALLOT_MEASURE')),
    poll_id BIGINT NOT NULL,
    scope_level scope_level NOT NULL,
    state_id BIGINT REFERENCES states(id),
    county_id BIGINT REFERENCES counties(id),
    zipcode VARCHAR(5),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT poll_purviews_scope_targets_match CHECK (
        (scope_level = 'ZIP'      AND zipcode IS NOT NULL AND county_id IS NULL AND state_id IS NULL)
     OR (scope_level = 'COUNTY'   AND county_id IS NOT NULL AND zipcode IS NULL AND state_id IS NULL)
     OR (scope_level = 'STATE'    AND state_id IS NOT NULL AND zipcode IS NULL AND county_id IS NULL)
     OR (scope_level = 'NATIONAL' AND zipcode IS NULL AND county_id IS NULL AND state_id IS NULL)
    )
);

CREATE INDEX idx_poll_purviews_poll ON poll_purviews(poll_type, poll_id);

-- Backfill elections: their single zipcode -> a COUNTY-level purview row (the
-- county containing that zip), so existing elections keep a meaningful scope.
-- A zip that resolves to no county is simply skipped (the service treats a poll
-- with no purview rows as NATIONAL).
INSERT INTO poll_purviews (poll_type, poll_id, scope_level, county_id)
SELECT 'ELECTION', e.id, 'COUNTY'::scope_level, cz.county_id
FROM elections e
JOIN LATERAL (
    SELECT county_id FROM county_zips WHERE zipcode = e.zipcode LIMIT 1
) cz ON true;

-- Backfill questionnaires: one ZIP-level purview row per existing domain zip.
INSERT INTO poll_purviews (poll_type, poll_id, scope_level, zipcode)
SELECT DISTINCT 'QUESTIONNAIRE', d.questionnaire_id, 'ZIP'::scope_level, d.zipcode
FROM questionnaire_domains d;
