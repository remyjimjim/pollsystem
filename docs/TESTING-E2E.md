# End-to-end testing (Playwright)

How the browser-level e2e suite works: the naming convention, the CLI tokens,
the **seed toolkit** (our little setup DSL), and how it differs per environment.
The specs live in `frontend/e2e/`; they drive a real browser through real user
journeys, and double as **screen-recordable how-tos** (headed + paced with
`hold=`). For the *coverage matrix* (which use cases have e2e), see
[`E2E-TRACEABILITY.md`](E2E-TRACEABILITY.md).

---

## Quick start

> **Run from `frontend/`, not `frontend/e2e/`.** Playwright reads
> `playwright.config.ts` only from the directory you launch it in (it does not
> search parent folders). Launched from `e2e/`, it silently falls back to
> defaults and **every CLI token below is ignored** (`hold`, `state`, … stop
> working, with no error). Confirm the config is picked up: the test title
> reflects your `state=` (e.g. `state=iowa` → `…results (iowa)…`).

Prereqs: the app is up in **`local`** or **`local-docker`** (both run the
backend under `SPRING_PROFILES_ACTIVE=local`, so the dev seed endpoints exist
and Stripe is mocked), with Mailpit at `:8025`, backend `:8080`, Vite `:3000`.

```bash
cd frontend
npx playwright test user-registers-submits-poll state=colorado --headed
npx playwright test viewer-searches-views-results state=colorado hold=6000 --headed
```

---

## Conventions

### Spec naming — `{actor}-{process}.spec.ts`

An e2e spec is one **journey**: an *actor* (a role — viewer / user / creator /
admin) performing a *process*. The verb in the name signals reset behavior:

- **Name contains `registers`** → the script *creates* its actor, so it
  **pre-wipes** that user in `beforeAll` (a clean slate to register into).
  `keep=yes` skips the pre-wipe.
- **No `registers`** (reuse / read-only scripts) → **never wipes**; it signs in
  as an already-seeded user (or just browses as a guest) and asserts the seed
  exists.

### Reset model

- The global teardown **leaves data in place by default** — seeded users persist
  for the next script and for manual inspection. Pass **`wipe=yes`** to clear
  `zzz`-prefixed users after the run. Cleanliness is otherwise owned by each
  `registers` script's pre-wipe.
- **`keep` means "don't wipe."** All seeded fixtures are `zzz`-prefixed;
  `reset-test-users` deletes by that prefix and cascades to their polls/responses.

### CLI tokens

Passed as bare `key=value` args (Playwright treats them as filename filters that
match nothing, so they don't change spec selection). `playwright.config.ts`
parses them into env vars — **which is why they only work when run from
`frontend/`** (see Quick start).

| Token | Env var | Effect | Default |
|---|---|---|---|
| `state=<name\|initial>` | `E2E_STATE` | Which state to place users in (full name or 2-letter initial, case-insensitive) | `colorado` |
| `county=<name>` | `E2E_COUNTY` | Narrow the seeded zip to a county | first county |
| `keep` / `keep=yes` | `E2E_KEEP` | Skip a `registers` script's pre-wipe | off |
| `wipe` / `wipe=yes` | `E2E_WIPE` | Wipe `zzz` users in the global teardown | off (leave data) |
| `hold=<ms>` | `E2E_HOLD_MS` | Per-screen pause for headed runs (0 = off) | `1500` |

> Spec bodies run in **worker** processes that don't inherit CLI positionals, so
> the config sets these as env vars in the main process; workers inherit them.

### Running the whole suite, and CI

`npx playwright test` (no file name) runs every spec, **one at a time**
(`workers: 1` in `playwright.config.ts`). Specs share state, so they must not
overlap: each clears Mailpit (which would delete another spec's magic link), and
`super-disables-submissions` flips a global kill switch (it re-enables it in
`afterAll`, so a failure there can't break the specs after it).

CI (`.github/workflows/ci.yml`, job **E2E (Playwright)**) runs exactly that, so a
new spec joins CI automatically. With `CI=true` the config skips the interactive
`seed-users-debug` spec, and the per-screen `hold` pauses are off.

### Watchable / recordable runs

`hold=<ms>` pauses on each key screen — only for `--headed`/interactive runs
(skipped when `CI=true` or `hold=0`). For a screen-recorded how-to (e.g. Awesome
Screenshot → `.mp4` + voiceover), `hold=6000`–`8000` gives a calm pace.

---

## The seed toolkit (`frontend/e2e/seed.ts`)

Shared setup helpers so specs stay short. Seeding is done **via the API**
(dev endpoints), invisible to the recorded browser session.

| Helper | What it does |
|---|---|
| `resolveLocation(state?, county?)` | A **real** zipcode (+ state id) via the public geography API. Required — `register-checkout` rejects zips absent from `county_zips`. |
| `seededEmail(role, i?, state?)` | The canonical address `zzz{i}-test{role}-{state}@protonmail.com`. |
| `registerAndSignIn(page, {email,phone,zipcode})` | Real UI pay-first registration → magic-link sign-in. |
| `signInSeededUser(page, email)` | Sign in an existing seeded user via `/login` → magic link (no reset). |
| `seedUser({access?, zipcode?, prefix?, adminStateId?})` | Create a paid member at any access level via the API; returns `{id, email, token}`. A `CREATOR` gets nationwide creator access; `adminStateId` gives an `ADMIN` state-wide admin access. |
| `devToken(email)` / `devUser(id)` | A JWT for an existing local user / a user's email + access by id (dev-only endpoints). |
| `latestCreatorRequest(email)` | The member's newest creator request (`status`, `assignedAdminId`). |
| `setPollsDisabled(superEmail, disabled)` | Flip the global kill switch via the API (cleanup safety net). |
| `resetTestUsers(prefix?)` | Wipe `zzz` users + everything anchored to them. |
| `seedQuestionnaire(prefix?)` | Seed one published questionnaire; returns its unique title. |
| `seedBallotMeasure({zipcode, prefix?})` | Seed a published ballot measure (creates a draft election to hang it on). |
| `seedBallotResponses({measureId, count, zipcode, prefix?})` | Add responses from real registered users (see rules below). |
| `hold(page, ms?)` | Watchable pause (interactive-only). |

Mail helpers live in `frontend/e2e/mailpit.ts`: `clearMailpit()`,
`fetchMagicLink(recipient)`, and `waitForEmail(recipient, subject)`.

### Seeding rules (non-negotiable)

- **Responses come from REAL registered users.** If the DB has none/too few,
  they're **created via the API** — never fabricated/anonymous response rows.
- **Never seed more than 6 responses.** That keeps totals under the
  **k-anonymity threshold of 10** (`app.results.k-anonymity-threshold`), so a
  purview/geo-filtered results view correctly **withholds** the tally. (Note:
  the *unfiltered* results view shows real counts even below 10 — suppression
  only fires when the view is narrowed: unticking "outside the poll's purview"
  (or "within"), or applying a geo filter.)
- **Zips must be real** (present in `county_zips`) — always source them from
  `resolveLocation`.

New poll kinds / parameters (e.g. `seedElection`, `seedUser`, richer response
options) get their `DevController` endpoint + `seed.ts` wrapper built with the
first spec that needs them.

---

## The specs today

| Spec | Actor | Journey |
|---|---|---|
| `register-users` | — | Bulk seeder: 8 users (2 per role) in a state; `keep`/`state`/`county` aware. |
| `user-registers-submits-poll` | user | Register + pay → find a poll by title → submit a response. |
| `viewer-searches-views-results` | viewer (guest) | Search → open results → show the k-anonymity floor (tally shown, then withheld under purview). |
| `user-submits-creator-request` | user | Request creator access for a whole state. |
| `admin-approves-creator` | admin | Member is turned away from the poll wizard → requests creator access → the admin it was routed to approves it in the queue → member gets the email and can open the wizard. |
| `creator-creates-questionnaire` | creator | Build a whole-state questionnaire in the wizard → publish → dashboard shows PUBLISHED → a guest finds it in search. |
| `super-disables-submissions` | super | Kill switch: submissions blocked for a member, then re-enabled. |
| `seed-users-debug` | — | Debug variant of the seeder (keeps windows open). |

**Roadmap:** see `docs/E2E-TRACEABILITY.md` ("Recommended e2e build order").
Hand over pseudo-code in the seed-toolkit vocabulary and the endpoints/helpers
get built to match.

---

## Environments

### Local (where the suite runs today)

The suite depends on `DevController`, which is **`@Profile("local")`** — its
`reset-test-users` / `seed-*` endpoints exist **only** under the `local` profile
(they 404 on staging/prod). So today's specs are inherently local.

- **After changing a dev endpoint**, rebuild the backend so the running JAR has
  it: `./scripts/BuildAndDeploy.bash down && ./scripts/BuildAndDeploy.bash
  local-docker`, or just the backend: `docker compose --profile app up -d
  --build backend`. (A 404 like *"No static resource api/dev/…"* means a stale
  backend.)
- See [`RUNNING-on-host.md`](RUNNING-on-host.md) /
  [`RUNNING-on-docker.md`](RUNNING-on-docker.md) for bringing the stack up.

### Staging (future)

No dev endpoints, so no seeding. A staging suite would drive **real**
registration through **Stripe test mode** and read magic links from the staging
mail sink — i.e. journeys only, no fixtures. Kept separate in scope because the
setup path is fundamentally different from local.

### Production (future — low priority)

**Strictly read-only smoke checks.** Absolutely no create / update / delete, no
test users, no seeding — only things safe to do against real production data:
load key pages, exercise public search/results rendering, confirm auth gates
redirect. Any spec here must be incapable of mutating prod state.
