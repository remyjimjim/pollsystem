-- V23__election_zipcode_nullable.sql
-- Elections move to a County/State/National purview (poll_purviews) as their
-- source of truth, so the legacy single zipcode is no longer required. Drop the
-- NOT NULL; existing rows keep their zip (and their backfilled COUNTY purview),
-- while newly created/edited elections store a coarse purview and leave zipcode
-- null.
ALTER TABLE elections ALTER COLUMN zipcode DROP NOT NULL;
