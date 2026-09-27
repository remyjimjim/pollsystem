-- V20__poll_block_everywhere.sql
-- Adds an EVERYWHERE block scope: a whole-poll block with no geographic
-- discriminator (zipcode/county/state all NULL). This is what a block already
-- does in practice — PollBlockService hides the whole poll for any block row —
-- so EVERYWHERE is the honest, explicit label for that behavior.
--
-- Widens the two CHECK constraints from V14 and adds a partial unique index so
-- a poll has at most one EVERYWHERE block.

ALTER TABLE poll_type_blocks
    DROP CONSTRAINT poll_type_blocks_scope_check,
    ADD CONSTRAINT poll_type_blocks_scope_check
        CHECK (scope IN ('ZIPCODE','COUNTY','STATE','EVERYWHERE'));

ALTER TABLE poll_type_blocks
    DROP CONSTRAINT poll_type_blocks_scope_targets_match,
    ADD CONSTRAINT poll_type_blocks_scope_targets_match CHECK (
        (scope = 'ZIPCODE'    AND zipcode IS NOT NULL AND county_id IS NULL AND state_id IS NULL)
     OR (scope = 'COUNTY'     AND county_id IS NOT NULL AND zipcode IS NULL AND state_id IS NULL)
     OR (scope = 'STATE'      AND state_id IS NOT NULL AND zipcode IS NULL AND county_id IS NULL)
     OR (scope = 'EVERYWHERE' AND zipcode IS NULL AND county_id IS NULL AND state_id IS NULL)
    );

-- One EVERYWHERE block per poll (mirrors the per-scope unique indexes in V15).
CREATE UNIQUE INDEX uq_poll_type_blocks_everywhere
    ON poll_type_blocks(poll_type, poll_id) WHERE scope = 'EVERYWHERE';
