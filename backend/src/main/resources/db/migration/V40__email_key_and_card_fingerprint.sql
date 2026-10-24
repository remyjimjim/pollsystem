-- One account per inbox: aliases of the same mailbox share an email key.
--   * googlemail.com is gmail.com; protonmail.com / protonmail.ch / pm.me are
--     proton.me; me.com / mac.com are icloud.com.
--   * Providers that deliver "name+tag@" to "name@" lose the +tag.
--   * Gmail ignores dots in the name.
-- The app checks with the same function (UserRepository.existsByEmailKey);
-- this index backs it up.
CREATE FUNCTION email_key(email TEXT) RETURNS TEXT
LANGUAGE sql IMMUTABLE STRICT AS $$
    WITH p AS (
        SELECT lower(split_part(email, '@', 1)) AS l,
               CASE lower(split_part(email, '@', 2))
                   WHEN 'googlemail.com' THEN 'gmail.com'
                   WHEN 'protonmail.com' THEN 'proton.me'
                   WHEN 'protonmail.ch'  THEN 'proton.me'
                   WHEN 'pm.me'          THEN 'proton.me'
                   WHEN 'me.com'         THEN 'icloud.com'
                   WHEN 'mac.com'        THEN 'icloud.com'
                   ELSE lower(split_part(email, '@', 2))
               END AS d
    ), q AS (
        SELECT d,
               CASE WHEN d IN ('gmail.com', 'proton.me', 'outlook.com', 'hotmail.com', 'live.com', 'icloud.com', 'fastmail.com')
                    THEN split_part(l, '+', 1) ELSE l END AS l
        FROM p
    )
    SELECT CASE WHEN d = 'gmail.com' THEN replace(l, '.', '') ELSE l END || '@' || d FROM q
$$;

CREATE UNIQUE INDEX users_email_inbox_key ON users (email_key(email));

-- The card a member paid with (Stripe's card fingerprint), so accounts paid
-- for with the same card can be flagged for review. Not unique: a household
-- may legitimately share a card.
ALTER TABLE users ADD COLUMN card_fingerprint VARCHAR(64);
CREATE INDEX idx_users_card_fingerprint ON users (card_fingerprint) WHERE card_fingerprint IS NOT NULL;
