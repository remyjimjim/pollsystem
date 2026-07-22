-- Paid-onboarding: a user provisioned from a Stripe payment starts with only an
-- email, and supplies phone + zipcode later by completing their profile at first
-- magic-link sign-in. Phone and zipcode therefore become optional at the row
-- level; the application enforces completeness before a user may participate
-- (submit responses, create polls).
--
-- phone keeps its UNIQUE constraint: Postgres treats NULLs as distinct, so any
-- number of not-yet-completed users (all NULL phone) coexist without collision.
ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE users ALTER COLUMN zipcode DROP NOT NULL;
