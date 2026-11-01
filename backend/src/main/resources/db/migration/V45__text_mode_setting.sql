-- V45__text_mode_setting.sql
-- Moderation (decided 2026-11-01): the mode for listed words in titles,
-- summaries, questions, office names, reasons, notes and messages, picked by
-- a super on the Super dashboard. BLOCK = No sends you back to reword it (the
-- save is refused); WARN = No saves it as typed. NULL = the configured
-- default (moderation.text-mode / MODERATION_TEXT_MODE). Takes effect at once.
ALTER TABLE app_flags ADD COLUMN text_mode VARCHAR(5)
    CHECK (text_mode IN ('BLOCK', 'WARN'));
