# pollsystem — Spring Boot Architecture Cost Forecast

> Cost forecast for the **Spring Boot 3 / Kotlin / Java 17** backend on **PostgreSQL**
> that implements the domain model in `docs/UML/Class-diagram.plantuml` —
> a multi-tenant poll-creation platform with users, roles, drafts, and approvals.
>
> The headline target is a **staging deployment that scales to 5,000 concurrent
> users and a maximum registered user pool of 200,000**, on the **lowest-cost
> credible path**. Auth is **magic-link only** (no passwords, no SMS); paid access
> is gated by **Stripe** subscriptions originating from a Substack-driven funnel.
> Estimates are in USD and reflect public pricing as of early 2026.

> **Mid-2026 pricing refresh (2026-07-22, +Redis note 2026-08-01).** A validation
> pass re-checked live pricing. The stack still holds up, with these corrections
> baked into the tables below:
> - **Redis/Upstash isn't actually used.** The app is stateless-JWT + Postgres-
>   backed magic-link tokens + an in-process role cache — it never calls Redis.
>   Dropped from the plan below (saves ~$5–10/mo); add it only when scaling to
>   multiple backend instances (shared cache / rate-limiting).
> - **SendGrid's free tier no longer exists** — it's now a 60-day trial, then
>   Essentials ~$19.95/mo. Switch magic-link delivery to **Resend** (free 3,000
>   emails/mo, 100/day) or **Amazon SES** (~$0.10 per 1,000 emails ≈ $10/mo at
>   100k sends). This is the one change that actually breaks the old $0 email line.
> - **Frontend → Cloudflare Pages (free)** instead of a Fly app — global CDN, $0,
>   one fewer machine to run.
> - **Watch two things:** Neon's bill is cheap only while it autosuspends; a
>   steady-traffic always-on 1 CU is ~$78/mo, so monitor CU-hours. And always use
>   Neon's **pooled** connection endpoint — a JVM HikariCP pool × N autoscaled
>   instances will otherwise exhaust Postgres connections. If Neon's always-on
>   cost climbs, a fixed-price Postgres (Supabase Micro ~$25, or self-managed on a
>   Hetzner box) gets cheaper. See "Alternatives considered" for the full comparison.

---

## TL;DR — Lowest-cost staging path

| Layer | Choice | Monthly |
|---|---|---|
| Backend (JVM) | Spring Boot compiled with **GraalVM native image**, 3–5× Fly.io shared-cpu-1x @ 512 MB, autoscale | ~$15–$25 |
| Frontend | **Cloudflare Pages** (static Vue build, global CDN) | **$0** |
| Database | **Neon Postgres — Launch plan** (10 GB, autoscaling compute) | ~$19 |
| Cache / sessions / tokens | **Not used** — JWT sessions + Postgres tokens + in-process cache (add Redis only at multi-instance scale) | **$0** |
| Email (magic-link delivery + transactional) | **Resend** free tier (3 k/mo) → **Amazon SES** (~$10 at 100k) once volume grows | $0–$10 |
| Phone / SMS | **None** — phone is collected and formatting-validated only | $0 |
| Payments | **Stripe** — no flat fee, per-transaction only (see below) | $0 fixed |
| CI | GitHub Actions free tier | $0 |
| **Recurring infrastructure total** | | **~$35–$50 / month** |

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
2. **Lowest-cost path (recommended)** — GraalVM native image, Neon Postgres. Slightly more upfront engineering, dramatically smaller monthly bill. This is the configuration in the TL;DR above.

### Sizing assumptions (both configs)

- **5,000 concurrent users** ≈ 5,000 open HTTP connections, peak ~1,000–2,000 req/s assuming typical poll-response interaction patterns.
- **200,000 registered users** ≈ 1–3 GB of relational data (users, role assignments, polls, responses, audit rows) over the life of staging.
- Sessions are stateless JWTs and magic-link tokens live in Postgres, so backend instances are already stateless and autoscale freely — no shared store needed. (Redis would only be to share the in-process role cache across instances at scale.)
- Magic-link email volume at staging: ~1–4 sign-ins per active user per month. With ~500 active users in staging, that's ~500–2,000 emails/month — comfortably inside SendGrid free.
- Backend handles ~1,000 concurrent connections per shared-cpu-1x instance with virtual threads (Spring Boot 3.2+ on Java 17), so 3–5 instances cover peak with headroom.

### 1. Conservative baseline (no native image)

| Service | Tier / Config | Monthly Estimate |
|---|---|---|
| Fly.io — backend (JVM) | 3–5× shared-cpu-2x, **2 GB RAM**, autoscale | $60–$120 |
| Fly.io — frontend | 1× shared-cpu-1x, 256 MB RAM, auto-stop | ~$3 |
| Fly Postgres (managed) | dedicated-cpu-1x, 10 GB volume, daily snapshots | ~$30 |
| Database encryption (at rest) | Fly Postgres volume encryption — default-on, AES-256 | $0 |
| Redis | **Not used** (optional; add at multi-instance scale) | $0 |
| Resend (email) | Free tier (≤ 3,000/mo); Amazon SES ~$10 at 100k sends | $0–$10 |
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
| Redis | **Not used** (optional; add at multi-instance scale) | $0 |
| Resend (email) | Free tier (≤ 3,000/mo); Amazon SES ~$10 at 100k sends | $0–$10 |
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
**~$1,580** of one-time SMS cost from a full-population demo. Revisited
2026-10-05: at realistic member counts phone verification is cheap
(~$0.07 per new member); see **Phone verification (SMS)** below.

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

## Actual bills and an idle-production finding (October 2026)

Real invoices, as a reality check on the estimates above:

| Provider | August 2026 | September 2026 | What it is |
|---|---|---|---|
| Fly.io (pay as you go) | $9.49 | $11.54 | Mostly the **production** app's one always-on 2 GB machine |
| Neon (Launch, $0.106 / CU-hour) | $31.58 (297.84 CU-h) | $28.79 (271.52 CU-h) | Compute that almost never suspends |
| Resend | $0 | $0 | Free tier |
| **Total** | **~$41** | **~$40** | |

**Finding (2026-10-05):** production (`pollsystem-backend`, last deployed
2026-09-05) runs with `min_machines_running = 1`, so one machine is always up.
Fly health-checks it on `/actuator/health` every 15 s, and Spring Boot's health
check includes a database check by default, so the production Neon compute is
queried every 15 s and never scales to zero. Before launch that is ~$40/month
for an unused app; setting `min_machines_running = 0` until launch would save
roughly $35/month (staging already sleeps: both machines auto-stopped ~8 min
after a deploy). **Done 2026-10-05** (Fly release v9): `min_machines_running = 0`
applied by redeploying the image production was already running, so no new code
or migrations shipped. **Set it back to 1 at launch.**

---

## Year-one revenue vs costs — 'modest' projection (10 → 200 or 2,000 paid users)

Estimated 2026-10-05 for two growth paths, at **$10/month** with **no creator
discount** and no churn. Costs use the actual bills above: Fly ~$12/month
(production kept warm once there are users; a second machine in viral months
11–12), Neon ~$30/month (rising to $35–40 at viral scale), Resend free until
the viral path needs the $20 plan for its 100/day cap (month 10 on), Twilio
~$0.074 per *new* member (phone verification, next section), Stripe $0.59 per
$10 payment. Excludes taxes, chargebacks/refunds and anyone's time.

**Steady path (→ ~200 users):**

| Mo | Users | Revenue | Stripe | Fly | Neon | Resend | Twilio | Total costs | Net |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 10 | $100 | $5.90 | $12 | $30 | $0 | $0.74 | $48.64 | $51.36 |
| 2 | 15 | $150 | $8.85 | $12 | $30 | $0 | $0.37 | $51.22 | $98.78 |
| 3 | 20 | $200 | $11.80 | $12 | $30 | $0 | $0.37 | $54.17 | $145.83 |
| 4 | 25 | $250 | $14.75 | $12 | $30 | $0 | $0.37 | $57.12 | $192.88 |
| 5 | 30 | $300 | $17.70 | $12 | $30 | $0 | $0.37 | $60.07 | $239.93 |
| 6 | 50 | $500 | $29.50 | $12 | $30 | $0 | $1.48 | $72.98 | $427.02 |
| 7 | 80 | $800 | $47.20 | $12 | $30 | $0 | $2.22 | $91.42 | $708.58 |
| 8 | 110 | $1,100 | $64.90 | $12 | $30 | $0 | $2.22 | $109.12 | $990.88 |
| 9 | 140 | $1,400 | $82.60 | $12 | $30 | $0 | $2.22 | $126.82 | $1,273.18 |
| 10 | 170 | $1,700 | $100.30 | $12 | $30 | $0 | $2.22 | $144.52 | $1,555.48 |
| 11 | 185 | $1,850 | $109.15 | $12 | $30 | $0 | $1.11 | $152.26 | $1,697.74 |
| 12 | 200 | $2,000 | $118.00 | $12 | $30 | $0 | $1.11 | $161.11 | $1,838.89 |
| **Year** | | **$10,350** | **$610.65** | **$144** | **$360** | **$0** | **$14.80** | **$1,129.45** | **$9,220.55** |

**Viral path (→ ~2,000 users):**

| Mo | Users | Revenue | Stripe | Fly | Neon | Resend | Twilio | Total costs | Net |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 10 | $100 | $5.90 | $12 | $30 | $0 | $0.74 | $48.64 | $51.36 |
| 2 | 15 | $150 | $8.85 | $12 | $30 | $0 | $0.37 | $51.22 | $98.78 |
| 3 | 20 | $200 | $11.80 | $12 | $30 | $0 | $0.37 | $54.17 | $145.83 |
| 4 | 25 | $250 | $14.75 | $12 | $30 | $0 | $0.37 | $57.12 | $192.88 |
| 5 | 30 | $300 | $17.70 | $12 | $30 | $0 | $0.37 | $60.07 | $239.93 |
| 6 | 60 | $600 | $35.40 | $12 | $30 | $0 | $2.22 | $79.62 | $520.38 |
| 7 | 150 | $1,500 | $88.50 | $12 | $30 | $0 | $6.66 | $137.16 | $1,362.84 |
| 8 | 300 | $3,000 | $177.00 | $12 | $30 | $0 | $11.10 | $230.10 | $2,769.90 |
| 9 | 550 | $5,500 | $324.50 | $12 | $35 | $0 | $18.50 | $390.00 | $5,110.00 |
| 10 | 900 | $9,000 | $531.00 | $12 | $40 | $20 | $25.90 | $628.90 | $8,371.10 |
| 11 | 1,400 | $14,000 | $826.00 | $24 | $40 | $20 | $37.00 | $947.00 | $13,053.00 |
| 12 | 2,000 | $20,000 | $1,180.00 | $24 | $40 | $20 | $44.40 | $1,308.40 | $18,691.60 |
| **Year** | | **$54,600** | **$3,221.40** | **$168** | **$395** | **$60** | **$148** | **$3,992.40** | **$50,607.60** |

Takeaways:

- Fixed infrastructure (Fly + Neon, ~$42/month) is covered by **5 paying
  users**, so both paths are profitable from month 1.
- **Stripe is the largest cost** (6% of revenue, more than all providers
  combined); its fixed $0.30 per payment is what makes low price points
  expensive.
- Twilio phone verification is the smallest line ($15/year steady, $148 viral).

---

## Year-one revenue vs costs — 'bare bones' projection (1 → 12 paid users)

The floor case: **one** paid member in month 1 and **one more each month**,
ending the year at 12. Same variables as the 'modest' projection: $10/month, no
creator discount, no churn, Stripe $0.59 per payment, Fly ~$12/month and Neon
~$30/month (production kept warm), Resend free, Twilio ~$0.074 per new member.

| Mo | Users | Revenue | Stripe | Fly | Neon | Resend | Twilio | Total costs | Net | Cumulative |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 1 | $10.00 | $0.59 | $12.00 | $30.00 | $0.00 | $0.074 | $42.66 | −$32.66 | −$32.66 |
| 2 | 2 | $20.00 | $1.18 | $12.00 | $30.00 | $0.00 | $0.074 | $43.25 | −$23.25 | −$55.92 |
| 3 | 3 | $30.00 | $1.77 | $12.00 | $30.00 | $0.00 | $0.074 | $43.84 | −$13.84 | −$69.76 |
| 4 | 4 | $40.00 | $2.36 | $12.00 | $30.00 | $0.00 | $0.074 | $44.43 | −$4.43 | −$74.20 |
| 5 | 5 | $50.00 | $2.95 | $12.00 | $30.00 | $0.00 | $0.074 | $45.02 | $4.98 | −$69.22 |
| 6 | 6 | $60.00 | $3.54 | $12.00 | $30.00 | $0.00 | $0.074 | $45.61 | $14.39 | −$54.83 |
| 7 | 7 | $70.00 | $4.13 | $12.00 | $30.00 | $0.00 | $0.074 | $46.20 | $23.80 | −$31.04 |
| 8 | 8 | $80.00 | $4.72 | $12.00 | $30.00 | $0.00 | $0.074 | $46.79 | $33.21 | $2.17 |
| 9 | 9 | $90.00 | $5.31 | $12.00 | $30.00 | $0.00 | $0.074 | $47.38 | $42.62 | $44.78 |
| 10 | 10 | $100.00 | $5.90 | $12.00 | $30.00 | $0.00 | $0.074 | $47.97 | $52.03 | $96.81 |
| 11 | 11 | $110.00 | $6.49 | $12.00 | $30.00 | $0.00 | $0.074 | $48.56 | $61.44 | $158.25 |
| 12 | 12 | $120.00 | $7.08 | $12.00 | $30.00 | $0.00 | $0.074 | $49.15 | $70.85 | $229.09 |
| **Year** | | **$780.00** | **$46.02** | **$144.00** | **$360.00** | **$0.00** | **$0.89** | **$550.91** | **$229.09** | |

- **Loses money for the first 4 months** (worst cumulative point −$74.20 in
  month 4), turns monthly-profitable at **5 members (month 5)**, and has earned
  back the early losses by **month 8**. Year: **$229.09 net**.
- Nearly all of the cost is the fixed ~$42/month of keeping production warm, so
  that is the lever at this size. Letting production sleep when idle
  (`min_machines_running = 0`; see "Actual bills" above) would cut Fly + Neon
  to roughly $5/month (a rough estimate) and lift the year to about **$670
  net**, at the price of a ~10–30 s wake-up on the first visit after a quiet
  spell.

---

## Phone verification (SMS)

**Status: proposed, not built.** Admin ↔ creator communication stays email +
notes. SMS would be used only to establish that a member's phone number is real,
e.g. ahead of a big election. Process diagram (with per-step costs):
`docs/UML/Phone Verification-Activity.plantuml` (rendered `.svg` alongside).

Twilio list prices, US, checked 2026-10-05:

| Item | Price |
|---|---|
| SMS segment (long code / toll-free) | $0.0083 + carrier fee $0.0035–0.005 ≈ **$0.012–0.013** |
| Phone number rental | $1.15/month (long code), $2.15 (toll-free) |
| A2P 10DLC registration (own texts only) | Brand $4.50 (low volume) or ~$46 one-time; campaign vetting $15 one-time; campaign $1.50–10/month |
| **Verify** (Twilio sends + checks the code) | **$0.05 per successful verification** + $0.0083 per SMS |
| Lookup: format validation | Free |
| Lookup: Line Type Intelligence (mobile vs landline vs VoIP) | **$0.008** |
| Lookup: SMS-pumping risk score | Free in North America |

**Cost per verified member ≈ $0.066–0.074:** $0.05 Verify + ~1.15 texts
(~15% need a resend) × ~$0.0125 + $0.008 Lookup. Failed or abandoned attempts
cost only their texts (no $0.05). Verifying **once per member** (when the phone
is first entered), the monthly cost is **new members × ~$0.074** — see the
year-one tables above.

Load on our own infrastructure is negligible: ~2 API calls and one row update
per verification. 100K verifications ≈ 200K small requests spread over days.

**Election spike (100K members verifying in one month):**

| Item | That month |
|---|---|
| Fly | ~$30–60 (autoscale to 3–5 machines for the busy days) |
| Neon | ~$100–200 (more compute while busy; storage for 100K members < $1) |
| Resend | ~$90–160 (~200K sign-in emails) |
| **Twilio (100K verifications)** | **~$6,600–7,400** |
| **Total extra** | **~$6,900–7,800** |

Participation requires a paid membership (`ParticipationGuard`), so 100K
election members would bring ~$941K that month after Stripe fees; the spike is
< 1% of it. If an election ever allowed participation **without** paying, the
cost is ~$0.078 per voter with no revenue against it.

**Choices to make before building:**

- **Lookup only ($0.008)** proves a real mobile number (not VoIP) but not that
  the member holds it; **Lookup + Verify (~$0.07)** proves both.
- Use **Verify** rather than our own texts: no 10DLC paperwork (as we
  understand it), built-in SMS-pumping (toll-fraud) protection, and it handles
  election-scale bursts that would hit per-brand 10DLC throughput caps.
- **Price stability:** SMS prices have drifted up through carrier surcharges
  (10DLC fees introduced 2021–23; T-Mobile raised pass-through fees in January
  2026). Verify's flat $0.05 is the more predictable part. Doubling SMS prices
  would add ~$140/year on the viral path.

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

### F. Use Resend's free tier (SendGrid's is gone)
As of mid-2026 SendGrid no longer has a permanent free tier (60-day trial, then
Essentials ~$19.95/mo). Use **Resend** instead — its free tier is 3,000 emails/mo
(100/day), which covers staging-scale magic-link traffic. When steady-state login
volume outgrows that, **Amazon SES** at $0.10 per 1,000 emails (~$10/mo for 100k
sends) is the cheapest path at scale; Resend Pro ($20/mo, 50k) is the lower-effort
alternative.

---

## When to leave the lowest-cost staging configuration

The recommended config is sized for **staging-at-scale** and is intended to also
serve as effective production for the early phase of the product. Move up to a
dedicated production tier when **any one** of these holds:

- **≥ 1,000 paid subscriptions.** At ~$25/mo per sub that's ≥ $25 k/mo of revenue
  riding on the platform — operational risk justifies the upgrade.
- The 200 k user pool is exceeded by ≥ 2× and Neon Launch's storage is filling up.
- Steady-state DB CPU on Neon exceeds the Launch plan's autoscale ceiling.
- Magic-link email volume exceeds Resend's free tier (3,000/mo) sustainably —
  at which point move to Amazon SES (~$0.10 per 1,000).
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
| Resend | https://resend.com/pricing |
| Amazon SES | https://aws.amazon.com/ses/pricing/ |
| Twilio SMS (US) | https://www.twilio.com/en-us/sms/pricing/us |
| Twilio Verify | https://www.twilio.com/en-us/verify/pricing |
| Twilio Lookup | https://www.twilio.com/en-us/user-authentication-identity/pricing/lookup |
| Twilio A2P 10DLC fees | https://support.twilio.com/hc/en-us/articles/1260803965530 |

> Prices were last verified in early 2026 and may have changed. Always check the
> provider's current pricing page before budgeting a real deployment.
