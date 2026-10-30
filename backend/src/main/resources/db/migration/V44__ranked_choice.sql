-- V44__ranked_choice.sql
-- Ranked-choice voting (decided 2026-10-30; docs/VOTING-RANKED-CHOICE.md).
-- A race (an office within an election) can be ranked: voters rank its
-- candidates 1st, 2nd, ... and results run an instant-runoff count. Offices
-- are shared across elections ("U.S. Senator" serves every state), so the
-- flag belongs to the election + office pair, not the office.
CREATE TABLE ranked_races (
    election_id BIGINT NOT NULL REFERENCES elections(id) ON DELETE CASCADE,
    office_id   BIGINT NOT NULL REFERENCES offices(id),
    PRIMARY KEY (election_id, office_id)
);

-- A ranked vote: one row per ranked candidate, response = true, choice_rank
-- 1..n. NULL for single-choice races (all existing rows).
ALTER TABLE candidate_responses ADD COLUMN choice_rank SMALLINT;

-- The 2026 midterm: Alaska counts all its general-election races by ranked
-- choice (U.S. Senate, U.S. House at-large, Governor / Lt Governor, 4
-- candidates each). Maine stays single-choice: 2 candidates per race, so
-- ranking can't change the outcome. Matched by title and office name, so
-- this does nothing where that ballot isn't loaded (fresh local, tests).
INSERT INTO ranked_races (election_id, office_id)
SELECT DISTINCT e.id, c.office_id
FROM elections e
JOIN candidates c ON c.election_id = e.id
JOIN offices o ON o.id = c.office_id
WHERE e.title = '2026 Midterm Alaska'
  AND o.name LIKE '%(ranked-choice on the ballot; pick your first choice here)'
ON CONFLICT DO NOTHING;

-- Those offices are used by the Alaska ballot only: their names now say
-- "(ranked choice)" instead of "pick your first choice here".
UPDATE offices o
SET name = replace(o.name, '(ranked-choice on the ballot; pick your first choice here)', '(ranked choice)')
WHERE o.id IN (SELECT office_id FROM ranked_races)
  AND NOT EXISTS (
      SELECT 1 FROM candidates c
      WHERE c.office_id = o.id
        AND c.election_id NOT IN (SELECT election_id FROM ranked_races));

-- Public results come from a snapshot that moves in steps of k respondents;
-- it keeps each ranked answer's rank too, for the round-by-round count.
ALTER TABLE result_snapshot_answers ADD COLUMN choice_rank SMALLINT;
