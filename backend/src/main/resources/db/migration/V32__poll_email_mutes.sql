-- V32__poll_email_mutes.sql
-- A creator can pause the milestone emails (V31) per poll from the Creator
-- dashboard. A row here = paused; no row = emails on (the default). While
-- paused, a milestone is marked handled but not sent, so re-enabling only
-- affects future emails (missed ones are skipped, not caught up).

CREATE TABLE poll_email_mutes (
    id         BIGSERIAL PRIMARY KEY,
    poll_type  VARCHAR(20) NOT NULL CHECK (poll_type IN ('ELECTION','QUESTIONNAIRE','BALLOT_MEASURE')),
    poll_id    BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (poll_type, poll_id)
);
