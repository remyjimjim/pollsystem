# CivicChain — Spring Boot Architecture Cost Forecast

> Cost forecast for the **Spring Boot 3 / Kotlin / Java 17** backend on **PostgreSQL**
> that implements the domain model in `docs/UML/Class-diagram.plantuml` —
> a multi-tenant poll-creation platform with users, roles, drafts, and approvals.
>
> The headline target is a **staging deployment that scales to 5,000 concurrent
> users and a maximum registered user pool of 200,000**, on the **lowest-cost
> credible path**. Auth is **magic-link only** (no passwords, no SMS); paid access
> is gated by **Stripe** subscriptions originating from a Substack-driven funnel.
> Estimates are in USD and reflect public pricing as of early 2026.

---

## TL;DR — Lowest-cost staging path

| Layer | Choice | Monthly |
|---|---|---|
| Backend (JVM) | Spring Boot compiled with **GraalVM native image**, 3–5× Fly.io shared-cpu-1x @ 512 MB, autoscale | ~$15–$25 |
| Frontend | 1× Fly.io shared-cpu-1x @ 256 MB, auto-stop | ~$3 |
| Database | **Neon Postgres — Launch plan** (10 GB, autoscaling compute) | ~$19 |
| Cache / sessions / magic-link tokens | **Upstash Redis** pay-as-you-go (~3–5 M cmds/mo) | ~$5–$10 |
| Email (magic-link delivery + transactional) | **SendGrid** free tier (3 k/mo) — sufficient for staging | $0 |
| Phone / SMS | **None** — phone is collected and formatting-validated only | $0 |
| Payments | **Stripe** — no flat fee, per-transaction only (see below) | $0 fixed |
| CI | GitHub Actions free tier | $0 |
| **Recurring infrastructure total** | | **~$42–$57 / month** |

This config will hold 5,000 concurrent users and a 200 k registered-user pool with
headroom on autoscale. Stripe takes a per-transaction cut on the revenue side
rather than the cost side — see the **Stripe fees** section.

---

## Local Environment

> Target: 10 concurrent users, 100 total users, 5–10 polls. Runs entirely on the developer machine.

| Service | Usage | Cost |
|---|---|---|
| Docker Desktop | Local containers (Postgres, optional Redis) | $0 |
| PostgreSQL (container) | Local DB with seed data; provisioned by `docker compose up db` | $0 |
| Redis (container, optional) | Magic-link token store + cache; in-memory map is fine for unit dev | $0 |
| Mailpit / MailHog | Fake SMTP for local magic-link testing — clicks resolve to `http://localhost:3000` | $0 |
| Stripe CLI | Forwards real Stripe webhooks to `localhost:8080/webhooks/stripe` for local dev | $0 |
| Electricity / hardware | Developer machine (JVM uses ~1 GB RAM idle) | negligible |
| **Monthly total** | | **$0** |

Notes:
- A laptop with ≥ 8 GB RAM is comfortable; ≥ 16 GB is recommended once frontend, backend, Postgres, and Redis are all running.
- Spring Boot DevTools live-reload covers JPA entity changes; bigger schema changes go through Flyway migrations.
- Use a **Stripe test-mode** key locally; real money never moves. The Stripe CLI tunnels webhook events to your localhost without exposing your machine to the internet.

---

## Staging Environment

> Target: **5,000 concurrent users**, **up to 200,000 registered users**, ~500 active polls.
> Hosted on Fly.io, with managed Postgres and Redis.

Two configurations are presented:

1. **Conservative baseline** — plain Spring Boot on the JVM, managed Fly Postgres. Easy to operate, no native-image build, but ~2× the recurring cost.
2. **Lowest-cost path (recommended)** — GraalVM native image, Neon Postgres, Upstash Redis. Slightly more upfront engineering, dramatically smaller monthly bill. This is the configuration in the TL;DR above.

### Sizing assumptions (both configs)

- **5,000 concurrent users** ≈ 5,000 open HTTP connections, peak ~1,000–2,000 req/s assuming typical poll-response interaction patterns.
- **200,000 registered users** ≈ 1–3 GB of relational data (users, role assignments, polls, responses, audit rows) over the life of staging.
- Sessions and magic-link tokens kept in Redis so backend instances stay stateless and can autoscale freely.
- Magic-link email volume at staging: ~1–4 sign-ins per active user per month. With ~500 active users in staging, that's ~500–2,000 emails/month — comfortably inside SendGrid free.
- Backend handles ~1,000 concurrent connections per shared-cpu-1x instance with virtual threads (Spring Boot 3.2+ on Java 17), so 3–5 instances cover peak with headroom.

### 1. Conservative baseline (no native image)

| Service | Tier / Config | Monthly Estimate |
|---|---|---|
| Fly.io — backend (JVM) | 3–5× shared-cpu-2x, **2 GB RAM**, autoscale | $60–$120 |
| Fly.io — frontend | 1× shared-cpu-1x, 256 MB RAM, auto-stop | ~$3 |
| Fly Postgres (managed) | dedicated-cpu-1x, 10 GB volume, daily snapshots | ~$30 |
| Database encryption (at rest) | Fly Postgres volume encryption — default-on, AES-256 | $0 |
| Upstash Redis | Pay-as-you-go (~3–5 M commands/month) | ~$5–$10 |
| SendGrid | Free tier (≤ 3,000 emails/month) | $0 |
| Object storage (R2 / S3) | Static assets, JSON exports, ~5 GB | ~$1 |
| Backup storage | Off-site Postgres snapshots, ~10 GB retained | ~$1 |
| GitHub Actions | Free tier (longer Java builds may push into paid mins) | $0–$4 |
| **Monthly total** | | **~$100–$170 / month** |

### 2. Lowest-cost path (recommended)

| Service | Tier / Config | Monthly Estimate |
|---|---|---|
| Fly.io — backend (GraalVM native image) | 3–5× shared-cpu-1x, **512 MB RAM**, autoscale | ~$15–$25 |
| Fly.io — frontend | 1× shared-cpu-1x, 256 MB RAM, auto-stop | ~$3 |
| Neon Postgres — Launch | 10 GB storage, autoscaling compute, branching | ~$19 |
| Database encryption (at rest) | Neon storage encryption — default-on, AES-256 | $0 |
| Upstash Redis | Pay-as-you-go (~3–5 M commands/month) | ~$5–$10 |
| SendGrid | Free tier (≤ 3,000 emails/month) | $0 |
| GitHub Actions | Free tier; native-image build cached between runs | $0 |
| **Monthly total** | | **~$42–$57 / month** |

### Per-run / one-time costs (full 200,000-user demo)

With magic-link auth and no SMS, the only meaningful per-event cost is email
delivery. Stripe fees scale with paid conversions, not registrations.

| Service | Calculation | One-Time / Variable Cost |
|---|---|---|
| SendGrid (overage past free tier) | ~200 k login emails over the demo on **Essentials 100 k** tier ($19.95/mo) | ~$20 |
| Stripe fees | Only on actual paid conversions — see next section | variable |
| **One-time demo total (excluding Stripe)** | | **~$20** |

Compared with the previous SMS-based design, dropping phone verification removed
**~$1,580** of one-time SMS cost from a full-population demo. That saving is
permanent: there is no scenario in this architecture where Twilio is needed.

---

## Stripe fees

Stripe charges per successful payment, not per month. There is no fixed
infrastructure cost — fees come out of revenue.

**US standard pricing** (as of early 2026): **2.9 % + $0.30** per successful card
charge. Fees scale linearly with paid conversions.

| Plan price | Stripe fee per payment | Net to you per payment |
|---|---|---|
| $5 / month | ~$0.45 | ~$4.55 (91 %) |
| $10 / month | ~$0.59 | ~$9.41 (94 %) |
| $25 / month | ~$1.03 | ~$23.97 (96 %) |
| $50 / month | ~$1.75 | ~$48.25 (97 %) |

Two implications for the cost picture:

1. **Stripe is revenue-side, not cost-side.** It does not affect the recurring
   $42–$57 staging total; it just reduces gross margin on each paid sub.
2. **Higher price points are dramatically more efficient.** A $5/mo plan loses 9 %
   to Stripe; a $25/mo plan loses 4 %. If the early product is "$25/mo for creator
   features," every paid sub nets ~$24 against an infrastructure cost that doesn't
   move when you add the 51st sub.

**Optional add-ons** (skip unless needed):
- **Stripe Tax** — automatic VAT/sales-tax calculation. 0.5 % per transaction. Only relevant once you have EU/UK customers or pass US state economic-nexus thresholds.
- **Radar for Fraud Teams** — $0.07 per screened transaction. Default Radar is included free.
- **Billing** — Stripe's hosted invoicing/portal is free for the standard subscription model used here.

---

## Why GraalVM is the lever

The conservative baseline is dominated by JVM RAM. A plain Spring Boot service needs
~1–2 GB RAM per instance for comfortable headroom; 3–5 instances at that size on
Fly.io land in the $60–$120/mo range.

A native image compiled with GraalVM:

- Boots in ~100 ms (vs ~10–30 s for the JVM), so autoscaling reacts to spikes instead of running warm spares.
- Uses ~150–250 MB RAM per instance, letting you run on shared-cpu-1x.
- Runs the same Spring Boot code, with caveats: reflection-heavy libraries need explicit hints, and the build itself takes 3–10 minutes per release.

For staging at 5 k concurrent / 200 k registered, GraalVM saves roughly **$45–$95/month**
in recurring spend. The break-even point on engineering effort is typically 2–3 months.

If the team can't take on the native-image build today, ship the conservative baseline
first and cut over later — the application code is identical, and Spring Boot 3 ships
with AOT support out of the box (the GraalVM Native Build Tools Gradle plugin is the
only addition).

---

## Cost reduction options

### A. GraalVM native image (save ~$45–$95/mo)
Already the recommended path. Listed here for completeness — this is the dominant lever.

### B. Drop Postgres replicas
The recommended config already excludes a read replica. Only add one when you observe
DB CPU > 70 % in steady state or reports queries demonstrably slowing writes.

### C. Use Neon's free tier instead of Launch (save ~$19/mo)
Neon Free covers up to 500 MB of storage and limited compute hours. A 200 k-user pool
will exceed 500 MB once polls and responses accumulate, but for an empty / lightly seeded
staging environment in early development, Free is fine. Plan to upgrade before any load test.

### D. Auto-stop frontend and backend overnight
Fly machines can auto-stop on idle. For staging used during business hours only, this
roughly halves backend recurring spend. Native-image cold start is fast (~100 ms)
so auto-stop is essentially free in UX terms; on the JVM it adds 10–30 s to the first
request after wake-up.

### E. Self-host Postgres on a Fly volume
Skip managed Postgres entirely and run vanilla `postgres:16` on a Fly machine with a
volume. Saves ~$15–$30/mo but you own backups, point-in-time recovery, and version
upgrades. Not recommended above the local-development tier.

### F. Stay on SendGrid free
3,000 emails/month covers staging-scale magic-link traffic indefinitely. Only move to
SendGrid Essentials (100 k for $19.95/mo) when steady-state login volume exceeds the
free tier — which, for this product, won't happen until thousands of monthly active users.

---

## When to leave the lowest-cost staging configuration

The recommended config is sized for **staging-at-scale** and is intended to also
serve as effective production for the early phase of the product. Move up to a
dedicated production tier when **any one** of these holds:

- **≥ 1,000 paid subscriptions.** At ~$25/mo per sub that's ≥ $25 k/mo of revenue
  riding on the platform — operational risk justifies the upgrade.
- The 200 k user pool is exceeded by ≥ 2× and Neon Launch's storage is filling up.
- Steady-state DB CPU on Neon exceeds the Launch plan's autoscale ceiling.
- Magic-link email volume exceeds SendGrid's free tier sustainably.
- Compliance, audit, or uptime SLAs are introduced — at that point you want Fly
  Postgres dedicated, off-site backups, and a read replica.

At the production-tier step, **database encryption upgrades** from default at-rest
to **customer-managed keys (BYOK)** — AWS KMS or Fly's equivalent at ~$1/key/month
plus per-API-call fees. That's a compliance lever (SOC 2, HIPAA, PCI) rather than
a security lever in absolute terms; the default at-rest encryption is already
strong, but CMK lets you rotate keys on your own schedule and prove key control
during audits.

For most teams the migration path is: **lowest-cost staging-as-production →
conservative baseline (still single-tier) → true production tier with HA
Postgres, a read replica, and customer-managed encryption keys**. Each step is a
config change, not a rewrite.

---

## Pricing sources

| Service | Pricing page |
|---|---|
| Fly.io | https://fly.io/docs/about/pricing/ |
| Fly Postgres | https://fly.io/docs/postgres/managing/pricing/ |
| Upstash Redis | https://upstash.com/pricing |
| Neon Postgres | https://neon.tech/pricing |
| Supabase (alternative DB) | https://supabase.com/pricing |
| SendGrid | https://sendgrid.com/en-us/pricing |
| Stripe pricing | https://stripe.com/pricing |
| Stripe Tax | https://stripe.com/tax |
| GraalVM native image | https://www.graalvm.org/native-image/ |

> Prices were last verified in early 2026 and may have changed. Always check the
> provider's current pricing page before budgeting a real deployment.
