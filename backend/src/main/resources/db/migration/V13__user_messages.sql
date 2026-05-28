-- V13__user_messages.sql
-- Messages a Super can attach to a user, surfaced in /super/manage-users.
-- The latest row per user_id appears in the Msg column; the popup walks
-- the full history via Prev/Next. Body is capped at 2000 chars.

CREATE TABLE user_messages (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL REFERENCES users(id),
    body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
    emailed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_messages_user_created
    ON user_messages(user_id, created_at DESC);
