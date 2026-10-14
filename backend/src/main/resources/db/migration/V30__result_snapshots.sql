-- V30__result_snapshots.sql
-- Small-poll privacy (decided 2026-10-14, option C): public results stay hidden
-- until a poll has k respondents (app.results.k-anonymity-threshold, 10 in
-- prod), then update only in steps of k. Each step freezes a copy of every
-- answer here; public results (and their filtered views) are computed from
-- that copy, so a single new vote or a single changed answer is never visible
-- on its own. Supers see live results.
--
-- The respondent's zipcode is frozen too, so a later zipcode change can't
-- shift a filtered view between steps. Answers go with their user (CASCADE).

CREATE TABLE result_snapshots (
    id          BIGSERIAL PRIMARY KEY,
    poll_type   VARCHAR(20) NOT NULL CHECK (poll_type IN ('ELECTION','QUESTIONNAIRE','BALLOT_MEASURE')),
    poll_id     BIGINT NOT NULL,
    respondents INT NOT NULL,
    taken_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (poll_type, poll_id)
);

CREATE TABLE result_snapshot_answers (
    id          BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES result_snapshots(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    zipcode     VARCHAR(5),
    item_id     BIGINT NOT NULL,     -- question, candidate, or the measure itself
    response    TEXT NOT NULL        -- the answer text, or 'true'/'false'
);

CREATE INDEX idx_result_snapshot_answers_snapshot ON result_snapshot_answers (snapshot_id);
