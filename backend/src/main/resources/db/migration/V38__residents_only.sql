-- An election only residents of its purview may vote on (the 2026 midterms:
-- you vote on your own state's ballots). Its ballot measures follow it.
ALTER TABLE elections ADD COLUMN residents_only BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE elections SET residents_only = TRUE WHERE title LIKE '2026 Midterm%';
