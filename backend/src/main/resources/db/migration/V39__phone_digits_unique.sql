-- One account per phone number, however it's typed: "(360) 555-1234",
-- "360-555-1234" and "+1 360 555 1234" are the same number. Compared by the
-- last 10 digits, so a leading country code 1 doesn't matter. The app checks
-- the same way first (UserRepository.existsByPhone); this backs it up.
CREATE UNIQUE INDEX users_phone_digits_key
    ON users (right(regexp_replace(phone, '[^0-9]', '', 'g'), 10))
    WHERE phone IS NOT NULL;
