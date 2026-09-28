-- V24__drop_questionnaire_domains.sql
-- Retire questionnaire_domains: a questionnaire's geo now lives in poll_purviews
-- (unified with the other poll types). V22 already backfilled questionnaire ZIP
-- purviews from this table, and the write path keeps them in sync — this just
-- defensively re-backfills any questionnaire that still has domains but no
-- purview rows, then drops the table.

INSERT INTO poll_purviews (poll_type, poll_id, scope_level, zipcode)
SELECT DISTINCT 'QUESTIONNAIRE', d.questionnaire_id, 'ZIP'::scope_level, d.zipcode
FROM questionnaire_domains d
WHERE NOT EXISTS (
    SELECT 1 FROM poll_purviews p
    WHERE p.poll_type = 'QUESTIONNAIRE' AND p.poll_id = d.questionnaire_id
);

DROP TABLE questionnaire_domains;
