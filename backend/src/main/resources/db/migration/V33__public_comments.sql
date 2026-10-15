-- V33__public_comments.sql
-- A respondent can make an answer's comment public (shown anonymously on the
-- results page). Default private, as all comments were before. The server
-- stores TRUE only for a comment free of the words in moderation/bad-words.json.

ALTER TABLE question_responses  ADD COLUMN comment_public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE candidate_responses ADD COLUMN comment_public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ballot_responses    ADD COLUMN comment_public BOOLEAN NOT NULL DEFAULT FALSE;
