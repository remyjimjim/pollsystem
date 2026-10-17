-- V34__poll_events.sql
-- A poll's history (decided 2026-10-17): published, reopened, closed, archived,
-- restored, switched off/on by an admin, edited by an admin or super, each with
-- who (NULL = automatic, e.g. closing) and when. A publish records what changed
-- since the previous one (details) and the poll's state then (snapshot, JSON).
-- No FK on poll_id: it names a row in one of three tables (as in poll_purviews).

CREATE TABLE poll_events (
    id        BIGSERIAL PRIMARY KEY,
    poll_type VARCHAR(20) NOT NULL CHECK (poll_type IN ('ELECTION','QUESTIONNAIRE','BALLOT_MEASURE')),
    poll_id   BIGINT NOT NULL,
    event     VARCHAR(30) NOT NULL CHECK (event IN (
        'PUBLISHED','REOPENED','CLOSED','ARCHIVED','RESTORED',
        'SWITCHED_OFF','SWITCHED_ON','EDITED','PUBLISHED_BEFORE_HISTORY')),
    actor_id  BIGINT REFERENCES users(id) ON DELETE SET NULL,
    at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    details   TEXT,
    snapshot  TEXT
);
CREATE INDEX idx_poll_events_poll ON poll_events(poll_type, poll_id, id);
CREATE INDEX idx_poll_events_at ON poll_events(at DESC);

-- Polls already live when history began: their past publish dates weren't
-- recorded, so they get one marker rather than an invented date.
INSERT INTO poll_events (poll_type, poll_id, event)
    SELECT 'QUESTIONNAIRE', id, 'PUBLISHED_BEFORE_HISTORY' FROM questionnaires WHERE status::text IN ('PUBLISHED','CLOSED');
INSERT INTO poll_events (poll_type, poll_id, event)
    SELECT 'ELECTION', id, 'PUBLISHED_BEFORE_HISTORY' FROM elections WHERE status::text IN ('PUBLISHED','CLOSED');
INSERT INTO poll_events (poll_type, poll_id, event)
    SELECT 'BALLOT_MEASURE', id, 'PUBLISHED_BEFORE_HISTORY' FROM ballot_measures WHERE status::text IN ('PUBLISHED','CLOSED');
