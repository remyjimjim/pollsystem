-- V31__poll_notifications.sql
-- Creator reports, phase 3: milestone emails to a poll's creator, each sent at
-- most once per poll (UNIQUE):
--   RESULTS_VISIBLE  the poll reached k respondents, so public results appear
--   CLOSED           the poll closed; a short final report
-- No FK to the poll (three poll tables); rows for deleted polls are harmless.

CREATE TABLE poll_notifications (
    id        BIGSERIAL PRIMARY KEY,
    poll_type VARCHAR(20) NOT NULL CHECK (poll_type IN ('ELECTION','QUESTIONNAIRE','BALLOT_MEASURE')),
    poll_id   BIGINT NOT NULL,
    kind      VARCHAR(20) NOT NULL CHECK (kind IN ('RESULTS_VISIBLE','CLOSED')),
    sent_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (poll_type, poll_id, kind)
);
