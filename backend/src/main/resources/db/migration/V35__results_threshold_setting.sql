-- V35__results_threshold_setting.sql
-- Local development only (decided 2026-10-17): the Dev settings page can set
-- the small-poll privacy threshold (results hidden until k respondents, steps
-- of k, small groups withheld) without a restart. NULL = the configured value
-- (app.results.k-anonymity-threshold, 10). Only read under the `local` profile:
-- staging and prod ignore it even if set.

ALTER TABLE app_flags ADD COLUMN results_threshold INT
    CHECK (results_threshold BETWEEN 1 AND 100);
