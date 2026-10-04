-- V26__creator_disables.sql
-- An admin's "Enabled" switch on /admin/manage-creators. A row means admin
-- `admin_id` has disabled creator `creator_id` within that admin's purview:
-- the creator can't save or publish polls there (CreatorGrantGuard), and their
-- polls there were blocked at poll ∩ creator ∩ admin purview. The row is the
-- stored state, so re-enabling individual polls doesn't re-check the creator.
-- Removing it (re-checking) lifts both.

CREATE TABLE creator_disables (
    id         BIGSERIAL PRIMARY KEY,
    creator_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    admin_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (creator_id, admin_id)
);

CREATE INDEX idx_creator_disables_creator ON creator_disables (creator_id);
