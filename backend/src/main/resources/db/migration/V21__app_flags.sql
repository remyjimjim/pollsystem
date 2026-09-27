-- V21__app_flags.sql
-- Singleton table of global platform flags. Currently one flag:
-- polls_disabled — a Super-admin kill-switch that takes ALL public poll
-- endpoints (/api/polls/**) offline in an emergency (e.g. a poll consuming
-- excessive resources). Enforced by a request interceptor; unrelated to the
-- per-poll admin block feature.
--
-- Singleton is enforced by CHECK (id = 1): the table holds exactly one row.

CREATE TABLE app_flags (
    id INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    polls_disabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT REFERENCES users(id)
);

INSERT INTO app_flags (id) VALUES (1);
