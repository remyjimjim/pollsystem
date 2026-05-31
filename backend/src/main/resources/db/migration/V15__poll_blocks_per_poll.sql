-- V15__poll_blocks_per_poll.sql
-- Earlier blocks were stored at the (poll_type, scope, geo) bucket
-- level, so a ZIPCODE block hit every poll of that type that included
-- the zipcode. /admin/manage-polls toggles each row individually, so
-- the bucket model surprised admins: enabling one poll re-enabled
-- every poll in the same bucket. Per-poll scoping fixes that.
--
-- Existing rows are dev test data; safe to drop with the schema
-- change.

DELETE FROM poll_type_blocks;

ALTER TABLE poll_type_blocks ADD COLUMN poll_id BIGINT NOT NULL;

DROP INDEX IF EXISTS uq_poll_type_blocks_zipcode;
DROP INDEX IF EXISTS uq_poll_type_blocks_county;
DROP INDEX IF EXISTS uq_poll_type_blocks_state;

CREATE UNIQUE INDEX uq_poll_type_blocks_zipcode
    ON poll_type_blocks(poll_type, poll_id, zipcode) WHERE scope = 'ZIPCODE';
CREATE UNIQUE INDEX uq_poll_type_blocks_county
    ON poll_type_blocks(poll_type, poll_id, county_id) WHERE scope = 'COUNTY';
CREATE UNIQUE INDEX uq_poll_type_blocks_state
    ON poll_type_blocks(poll_type, poll_id, state_id) WHERE scope = 'STATE';
CREATE INDEX idx_poll_type_blocks_poll
    ON poll_type_blocks(poll_type, poll_id);
