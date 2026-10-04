-- V25__poll_creator_edited_at.sql
-- When the CREATOR last touched each poll (create, edit, publish, archive,
-- restore). Drives "last edit" on /admin/manage-creators. Deliberately separate
-- from ballot_measures.last_updated, which admin/super moderation edits also
-- bump: those must not count as the creator's own activity.
--
-- Backfilled from the best existing creator-side timestamp so existing polls
-- show a date instead of nothing: questionnaires use the later of creation
-- (a DATE) and first publish; elections their creation (date_submitted is set
-- at row creation); ballot measures their creation.

ALTER TABLE questionnaires   ADD COLUMN creator_edited_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE elections        ADD COLUMN creator_edited_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE ballot_measures  ADD COLUMN creator_edited_at TIMESTAMP WITH TIME ZONE;

UPDATE questionnaires  SET creator_edited_at = GREATEST(create_date::timestamptz, COALESCE(submit_date, create_date::timestamptz));
UPDATE elections       SET creator_edited_at = date_submitted;
UPDATE ballot_measures SET creator_edited_at = date_created;

ALTER TABLE questionnaires   ALTER COLUMN creator_edited_at SET NOT NULL, ALTER COLUMN creator_edited_at SET DEFAULT NOW();
ALTER TABLE elections        ALTER COLUMN creator_edited_at SET NOT NULL, ALTER COLUMN creator_edited_at SET DEFAULT NOW();
ALTER TABLE ballot_measures  ALTER COLUMN creator_edited_at SET NOT NULL, ALTER COLUMN creator_edited_at SET DEFAULT NOW();

-- Manage Creators aggregates MAX(creator_edited_at) per creator.
CREATE INDEX idx_questionnaires_creator_edited  ON questionnaires  (creator_id, creator_edited_at);
CREATE INDEX idx_elections_creator_edited       ON elections       (creator_id, creator_edited_at);
CREATE INDEX idx_ballot_measures_creator_edited ON ballot_measures (creator_id, creator_edited_at);
