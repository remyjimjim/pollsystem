# seed-demo.bash

Starts the **local** database over with a demo: users, creators and admins in
three states, each with draft polls. Everything goes through the real API, so
the access requests, approvals and polls are genuine records. Based on
`scripts/pseudocode/swagger-like-db-reset.txt`.

## Running it

- From a terminal on your machine: `./scripts/seed-demo.bash`
- From the Dev Container, where `localhost:8080` isn't reachable, run it inside
  a small container on the host network:

  ```bash
  docker run --rm --network host -v "$PWD":/repo -w /repo alpine:3.20 \
    sh -c "apk add -q bash curl python3 && bash scripts/seed-demo.bash"
  ```

It needs the local stack running (`BuildAndDeploy.bash local` or
`local-docker`). It's safe to re-run: each run first deletes the previous demo
users and the e2e `zzz…` test users, with everything they made. Every other
user (your own super, `admin@local.test`, …) is left alone.

## What it creates

All users are paid members at `demo-…@test.local`.

| User | Per state (`wash`, `colo`, `cali`) |
| --- | --- |
| `demo-super@test.local` | One overall: the super who approves the requests below. |
| `demo-respondent1..10-<st>` | 10 members in the state. |
| `demo-user-<st>` | A member. |
| `demo-creator-<st>` | Creator for the state (requested, then approved). Has a draft **"MidTerm Election \<State\>"** with 4 candidates (Jim James and Judy James in District 1; Barney Rubble and Fred Flintstone in District 2), 3 draft ballot measures on it (No nukes, No sales tax, No property tax), and 3 draft questionnaires (vaccines, money in politics, Glyphosate/Food) of 3 Yes/No questions each. |
| `demo-admin-<st>` | Creator for the state with the same 3 draft questionnaires, then admin for the state (requested, then approved). |

In total: 40 users (33 members, 3 creators, 3 admins, 1 super); 3 elections,
9 ballot measures and 18 questionnaires (54 questions), all drafts.

## Signing in as a demo user

**Quick sign-in:** the Login page (http://localhost:3000/login) shows a
**Quick sign-in (local only)** panel under the form, with a button per demo
user. One click signs you in, with no email. It's only in the local development
build, so it never appears on staging or prod.

Or the usual way: enter the address (e.g. `demo-creator-wash@test.local`), then
click the sign-in link in Mailpit (http://localhost:8025).

## How it works

- `/api/dev/seed-user` creates each user. Its `email` option gives an exact,
  memorable address; it only accepts `@test.local` addresses that start with the
  given prefix, so the next run's wipe can always find them.
- Creator and admin access go through the real request endpoints and are
  approved as `demo-super`.
- Polls are created through the normal creator endpoints as drafts. The
  election is created first and then edited to add its candidates.
- Any failed call stops the script with the server's message.

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
