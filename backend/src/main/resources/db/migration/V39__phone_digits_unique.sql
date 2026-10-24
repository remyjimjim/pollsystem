-- One account per phone number, however it's typed: "(360) 555-1234",
-- "360-555-1234" and "+1 360 555 1234" are the same number. Compared by the
-- last 10 digits, so a leading country code 1 doesn't matter. The app checks
-- the same way first (UserRepository.existsByPhone); this backs it up.
--
-- If some accounts already share a number (typed differently), a unique index
-- can't be built: the migration then builds a plain index and warns, rather
-- than stopping the app from starting. The app still refuses new duplicates.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM users WHERE phone IS NOT NULL
        GROUP BY right(regexp_replace(phone, '[^0-9]', '', 'g'), 10) HAVING count(*) > 1
    ) THEN
        RAISE WARNING 'V39: some accounts share a phone number; built a plain index, not a unique one';
        CREATE INDEX users_phone_digits ON users (right(regexp_replace(phone, '[^0-9]', '', 'g'), 10))
            WHERE phone IS NOT NULL;
    ELSE
        CREATE UNIQUE INDEX users_phone_digits_key ON users (right(regexp_replace(phone, '[^0-9]', '', 'g'), 10))
            WHERE phone IS NOT NULL;
    END IF;
END $$;
