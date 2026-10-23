-- V37__election_includes.sql
-- One ballot per district (decided 2026-10-23): an election can include other
-- elections, e.g. "2026 Midterm Colorado District 1" (its House race) includes
-- the state's statewide election (Senate, governor, measures). Voters see one
-- combined ballot, but each vote is still stored on its own election, so a
-- statewide race keeps statewide totals. An election included by a published
-- one is left out of poll search; voters reach it through their district.

CREATE TABLE election_includes (
    election_id          BIGINT NOT NULL REFERENCES elections(id) ON DELETE CASCADE,
    included_election_id BIGINT NOT NULL REFERENCES elections(id) ON DELETE CASCADE,
    position             INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (election_id, included_election_id),
    CHECK (election_id <> included_election_id)
);
CREATE INDEX idx_election_includes_included ON election_includes(included_election_id);
