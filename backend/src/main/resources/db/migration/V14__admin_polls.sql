-- V14__admin_polls.sql
-- Backing tables for /admin/manage-polls:
--   * poll_type_blocks  — declares that submissions for a given poll type
--                         are blocked at a zipcode / county / state scope.
--                         Any active block matching a poll's (type, zipcode)
--                         hides the poll from search/results and rejects
--                         new responses.
--   * poll_notes        — admin-written notes attached to a specific poll
--                         instance. Polymorphic: poll_type + poll_id refer
--                         to one of three poll tables. Notes are shared
--                         across admins; the latest preview shows in the
--                         row and history is reachable via Prev/Next.

CREATE TABLE poll_type_blocks (
    id BIGSERIAL PRIMARY KEY,
    poll_type VARCHAR(32) NOT NULL
        CHECK (poll_type IN ('ELECTION','QUESTIONNAIRE','BALLOT_MEASURE')),
    scope VARCHAR(16) NOT NULL
        CHECK (scope IN ('ZIPCODE','COUNTY','STATE')),
    zipcode VARCHAR(5),
    county_id BIGINT REFERENCES counties(id),
    state_id BIGINT REFERENCES states(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT NOT NULL REFERENCES users(id),
    -- Exactly one geographic identifier must be set, matching the scope.
    CONSTRAINT poll_type_blocks_scope_targets_match CHECK (
        (scope = 'ZIPCODE' AND zipcode IS NOT NULL AND county_id IS NULL AND state_id IS NULL)
     OR (scope = 'COUNTY'  AND county_id IS NOT NULL AND zipcode IS NULL AND state_id IS NULL)
     OR (scope = 'STATE'   AND state_id IS NOT NULL AND zipcode IS NULL AND county_id IS NULL)
    )
);

-- Three partial indexes — one per scope — give us unique-by-target and
-- avoid Postgres rejecting NULL!=NULL in a multi-column unique index.
CREATE UNIQUE INDEX uq_poll_type_blocks_zipcode
    ON poll_type_blocks(poll_type, zipcode) WHERE scope = 'ZIPCODE';
CREATE UNIQUE INDEX uq_poll_type_blocks_county
    ON poll_type_blocks(poll_type, county_id) WHERE scope = 'COUNTY';
CREATE UNIQUE INDEX uq_poll_type_blocks_state
    ON poll_type_blocks(poll_type, state_id) WHERE scope = 'STATE';

CREATE TABLE poll_notes (
    id BIGSERIAL PRIMARY KEY,
    poll_type VARCHAR(32) NOT NULL
        CHECK (poll_type IN ('ELECTION','QUESTIONNAIRE','BALLOT_MEASURE')),
    poll_id BIGINT NOT NULL,
    body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
    author_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_poll_notes_poll
    ON poll_notes(poll_type, poll_id, created_at DESC);
