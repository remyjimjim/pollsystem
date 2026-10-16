To run it again:
- From a terminal on your machine: ./scripts/seed-demo.bash
- From the Dev Container, where localhost:8080 isn't reachable, run it inside a small container:
  docker run --rm --network host -v "$PWD":/repo -w /repo alpine:3.20 sh -c "apk add -q bash curl python3 && bash scripts/seed-demo.bash"

Each run first deletes the previous demo and the e2e zzz users. Your own super and admin@local.test are left alone; I confirmed that.

What it creates, all through the real API:
- Users: demo-super@test.local approves the requests. Per state (wash, colo, cali), all paid members: demo-respondent1..10-<st>, demo-user-<st>, demo-creator-<st> and demo-admin-<st>.
- The creator requests creator access for the state, and the super approves it.
  - It then creates "MidTerm Election <State>" as a draft and edits it to add your 4 candidates in District 1 and District 2.
  - It also attaches 3 ballot measures (No nukes, No sales tax, No property tax) and creates 3 questionnaires (vaccines, money in politics, Glyphosate/Food), each with 3 Yes/No questions I wrote.
- The admin gets creator access, creates the same 3 questionnaires, then requests admin access for the state, approved by the super.

Checked in the database, twice (the second run cleanly replaced the first):
- 40 users: 33 members, 3 creators, 3 admins, 1 super;
- 3 elections with 4 candidates each, 9 measures, 18 questionnaires (54 questions), all drafts;
- every grant in the right state.

Notes:
- Logging in: enter e.g. demo-creator-wash@test.local at http://localhost:3000/login, then click the link in Mailpit (http://localhost:8025).
- One small backend addition: seed-user can now take an exact email address, so the demo users get memorable addresses. It only accepts @test.local addresses starting with the given prefix, so the wipe can always find them again. New tests cover it, and the backend suite passes 326/326.
- A hiccup in testing: the Kotlin compiler's background process crashed once, which left old test results in place and made a passing test look like it failed. A clean rerun confirmed everything passes.
- Your pseudocode file is untracked (scripts/pseudocode/). I didn't commit it, since it's your working note; the script's header credits it. Tell me if you'd like it in the repo.
- The "Seeding a demo database" section of docs/RUNNING-on-docker.md explains it.
## Running it on staging

**It doesn't run on staging today, and it has never been run there.** The
process differs from local-docker in three ways that stop it:

1. **No dev endpoints.** The script relies on `/api/dev/reset-test-users`,
   `/api/dev/seed-user` and `/api/dev/token`. Those exist only under the `local`
   Spring profile; staging runs the `staging` profile, which behaves exactly like
   prod, so they answer **404** there (checked 2026-10-16).
2. **Real email.** Staging sends sign-in links through Resend to real mailboxes.
   The demo users' `@test.local` addresses can't receive mail, so even if they
   were created, nobody could sign in as them. (Locally, Mailpit catches
   everything.)
3. **Real sign-up.** On staging a new user pays first, through Stripe test
   checkout, then signs in by email. There's no shortcut that skips either step.

Pointing the script at staging (`API=https://pollsystem-backend-staging.fly.dev
./scripts/seed-demo.bash`) just stops at the first dev call with a 404. That's
harmless: it changes nothing.

### If you want demo data on staging

Not built yet. What it would take, safest first:

- **A staging-only seeding switch.**
  - Make the seeding endpoints available under the `staging` profile too, but
    **only** when a secret seeding key (a Fly secret, on staging only) is sent
    with each call. They would never exist on prod.
  - Give the demo users **real addresses you own**, using "+" aliases of your
    own mailbox (for example `you+demo-creator-wash@yourmail`). Your provider
    delivers those to your inbox, so you can sign in as each one.
  - The script would then take the staging URL, the key, and the address
    pattern. Its wipe would only ever touch those demo addresses.
- **Copying a seeded local database to staging** (dump and restore into
  staging's Neon branch). This **replaces everything on staging**, and the
  `@test.local` users still couldn't sign in, so it's only useful for browsing
  as people who can already sign in there.
- **By hand, through the app.** Sign up each user with Stripe's test card,
  request access, approve it, and create the polls. Fine for a few users, but
  impractical for the script's 40.

**Never point any of this at prod.**
