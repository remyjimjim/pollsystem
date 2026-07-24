# Environments & test sites

**TL;DR:** we run **two environments — local + production**. A "test site" is a
separate *environment*, **not** a separate *domain* — you never buy a domain for
it. The preferred testing paths below are all free; an always-on online staging
site is an optional extra you only stand up if you actually need it.

Two key facts that clear up the usual confusion:

- **Test sites live on subdomains, which are free.** If you ever want an online
  test site it's `staging.surveysays.buzz` — a DNS record on a domain you already
  own — not a second registration. Subdomains are unlimited and cost $0.
- **`.dev` is not "the test TLD."** It's just a top-level domain Google operates;
  "test vs production" is a deployment concept with nothing to do with the TLD.
  There is no reason to own a `.dev` for testing.

The two domains this project needs are **`surveysays.buzz`** (the app) and
**`codehutch.org`** (the business). That's the full list.

---

## Preferred: the free testing paths (use these first)

### 1. Local — your primary test environment
The whole stack runs on your machine: Postgres + Mailpit + backend on the `local`
profile + the Vite dev server, with **Stripe in test mode** via the Stripe CLI.
This is where day-to-day testing happens. See `docs/DEPLOYING-LOCAL.md` and
`docs/STRIPE-TEST-RUNBOOK.md` (Part A).

### 2. Cloudflare Pages preview URLs — a free online frontend test site
Cloudflare Pages **automatically** builds every non-production branch / PR and
serves it at a unique `https://<hash>.<project>.pages.dev` URL — no domain, no
config. Share it, click around, throw it away. The production branch (`main`)
serves the production site; everything else is a preview.

> Caveat: a preview frontend uses the project's `BACKEND_ORIGIN`, so by default it
> talks to the **production** backend. That's fine for frontend/UI review. For a
> preview that must not touch prod data, set a **branch-scoped** `BACKEND_ORIGIN`
> pointing at a staging backend (see the optional section below).

### 3. Test-mode-first, on production infra — the go-live gate
Instead of a permanent staging box, verify the risky paths against the *real*
deployment while it's still on **Stripe test keys**, then flip to live keys. This
proves real webhook delivery and email deliverability without a standing
environment. The exact steps are `docs/STRIPE-TEST-RUNBOOK.md` (Part B).

**For most work, 1–3 are all you need** — that's the "local + production" model
(`docs/COSTS.md`), and it keeps the recurring bill and the ops surface small.

---

## Optional: an always-on staging site

Only worth it once you want a persistent, prod-like environment to sit on before
promoting changes (e.g. UAT, demoing to others, soak-testing migrations). It's
**still one domain** — a subdomain plus a second deploy.

**Shape:**

```
staging.surveysays.buzz  ──▶  Cloudflare Pages (staging branch/project)
                                └─ /api/* ─▶ pollsystem-backend-staging  (Fly app)
                                                 └─▶ Neon branch "staging"  (copy-on-write DB)
```

**Steps:**

1. **Staging backend** — a second Fly app, e.g. `pollsystem-backend-staging`, from
   the same `backend/Dockerfile`/`fly.toml` (override `app =`). Give it its own
   secrets: a **Neon branch** for the DB (Neon branching is copy-on-write, so a
   staging DB is cheap), Stripe **test** keys, and `APP_BASE_URL=https://staging.surveysays.buzz`.
2. **Staging frontend** — either a second Cloudflare Pages project bound to a
   `staging` branch, or the existing project with a **branch-scoped**
   `BACKEND_ORIGIN=https://pollsystem-backend-staging.fly.dev`.
3. **Subdomain** — add `staging.surveysays.buzz` as a custom domain on the staging
   Pages project (one DNS record; no new registration).

**Cost / when to skip:** a staging backend runs a second (small, auto-stop) Fly
machine plus some Neon compute — a few dollars a month. Until you have a concrete
need for a shared pre-prod environment, prefer paths 1–3 and don't pay for it.

---

See also: `docs/DEPLOYING-FLY.md` (backend), `docs/DEPLOYING-CLOUDFLARE-PAGES.md`
(frontend), `docs/STRIPE-TEST-RUNBOOK.md`, `docs/TESTING-FLY.md` (post-deploy
smoke tests), `docs/COSTS.md` (the local + production cost model).
