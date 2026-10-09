-- V29__disable_reasons.sql
-- Why access was switched off (decided 2026-10-08, "decision C"). Every
-- disable on Manage Creators asks for a reason, required when disabling your
-- own creator access (e.g. "On vacation until Nov 3"); re-enabling clears it.
--
-- role_assignments: a single creator grant switched off. disabled_by is kept
-- as a plain id (SET NULL if that user is deleted) so it never blocks deletes.
-- creator_disables: the row's Enabled switch (a whole creator, in one admin's
-- purview); who and when are already its admin_id and created_at.
-- (V28 is the local seed's, so this schema change is V29.)

ALTER TABLE role_assignments
    ADD COLUMN disabled_reason VARCHAR(500),
    ADD COLUMN disabled_by     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN disabled_at     TIMESTAMPTZ;

ALTER TABLE creator_disables
    ADD COLUMN reason VARCHAR(500);
