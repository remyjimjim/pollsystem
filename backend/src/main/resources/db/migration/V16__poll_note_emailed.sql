-- V16__poll_note_emailed.sql
-- Track whether a poll note was emailed to the poll's creator when
-- created, mirroring the `emailed` flag on user_messages so the two
-- flows behave alike.

ALTER TABLE poll_notes ADD COLUMN emailed BOOLEAN NOT NULL DEFAULT FALSE;
