# CivicChain — Spring Boot Architecture Cost Forecast

> This document is a **comparison companion** to `COSTS.md`. It estimates the cost of an
> alternative architecture: a **Spring Boot / Java 21** backend on top of **PostgreSQL**,
> implementing the domain model described in `docs/UML/Class-diagram.plantuml` —
> a multi-tenant poll-creation platform with users, roles, drafts, and approvals.
>
> The current production system (`COSTS.md`) is **not** the same product as this one.
> See the "Architecture Comparison" section below for what changes between them.
> Estimates are in USD and reflect public pricing as of early 2026.

---

## Architecture Comparison

| Concern | Current (Node + Irys) | Spring Boot model (this doc) |
|---|---|---|
| Domain | Single ballot, anonymous submissions | Multi-tenant poll platform: users create polls, others respond |
| Identity | None — phone+email dedup only | `User` table, `RoleAssignment`, `AccessLevel` (VIEWER → SUPER) |
| Auth | Stateless dedup keys; HTTP Basic on `/admin` | Sessions / JWTs; password-hashed login; admin approval workflow |
| Storage | Redis (state) + Irys/Arweave (encrypted payload) | PostgreSQL primary; Redis for cache/sessions only |
| Workflow | Submit form → encrypt → upload | Creator drafts poll → admin approves → publish → respondents answer |
| Stack | Node 20 + Express + TypeScript | Java 21 + Spring Boot 3 + Spring Data JPA + Hibernate |
| Frontend | React/Vite (kept as-is in either model) | React/Vite (kept as-is in either model) |
| Hosting | Fly.io shared-cpu-1x (256–512 MB) | Fly.io / AWS / Render — **min 1 GB RAM** per Java instance |
| Cold start | ~1–3s | ~10–30s (JVM warm-up) |
| Per-request CPU at idle | Very low (event loop) | Higher (thread-per-request, JIT warm-up cost) |
| Steady-state throughput | Excellent for I/O-bound | Excellent once warm; scales linearly with cores |

**The key takeaway**: the Spring model is more expensive at every scale tier because the JVM
needs more RAM, you add a real database, and the platform model has more features to host.
What you buy with that cost is a product that can have many polls, many users, role-based
features, audit history, and admin workflows — none of which the current architecture supports
without a substantial rewrite.

---

## Local Environment

> Target: 10 concurrent users, 100 total users, 5–10 polls. Runs entirely on your own machine.

| Service | Usage | Cost |
|---|---|---|
| Docker Desktop | Local containers (Postgres, Redis, backend, frontend, nginx) | $0 |
| PostgreSQL (container) | Local DB with seed data | $0 |
| Redis (container) | Cache + session store | $0 |
| Mailpit / Ethereal | Fake SMTP for local testing | $0 |
| Twilio (mocked) | SMS codes logged to console in `SPRING_PROFILES_ACTIVE=local` | $0 |
| Electricity / hardware | Developer machine (JVM uses ~1 GB RAM idle) | negligible |
| **Monthly total** | | **$0** |

**Notes:**
- A laptop with ≥ 8 GB RAM is comfortable; ≥ 16 GB is strongly recommended once frontend, backend, Postgres, and Redis are all running.
- Spring Boot DevTools live-reload covers JPA entity changes; bigger schema changes require a Postgres recreate.

---

## Staging Environment

> Target: 50 concurrent users, 1,000 total users, ~50 polls. Hosted on Fly.io.

### Recurring Monthly Costs

| Service | Tier / Config | Monthly Estimate |
|---|---|---|
| **Fly.io — backend (JVM)** | 1× shared-cpu-2x, **1 GB RAM**, always-on | ~$15.00 |
| **Fly.io — frontend** | 1× shared-cpu-1x, 256 MB RAM, auto-stop | ~$3.00 |
| **Fly Postgres (managed)** | shared-cpu-1x, 1 GB volume | ~$5.00 |
| **Upstash Redis** | Free tier (10k commands/day) | $0 |
| **SendGrid** | Free tier (100 emails/day) | $0 |
| **Twilio** | Trial credit ~$15.50 | ~$0–$1.50 |
| **GitHub Actions** | Free tier (2,000 min/month) | $0 |
| **Monthly total** | | **~$23–$25/month** |

### One-Time / Per-Run Costs (1,000 user demo)

| Event | Cost |
|---|---|
| SMS verifications (1,000 × $0.0079) | ~$7.90 |
| Email sends (≤3,000) | $0 (within free tier) |
| **One-time demo run total** | **~$7.90** |

**Comparison to current architecture (Node):**
- Current staging: ~$8–$10/month → Spring staging: ~$23–$25/month
- The delta (~$15) is JVM RAM (+$10) and managed Postgres (+$5).

---

## Production Environment

> Target: 5,000 concurrent users, 80,000 total responses, ~500 active polls.
> Hosted on Fly.io with autoscaling. Numbers below assume the platform supports
> creator/admin workflows, not just response collection.

### Recurring Monthly Costs

| Service | Tier / Config | Monthly Estimate |
|---|---|---|
| **Fly.io — backend (JVM)** | 2–6× shared-cpu-2x, **2 GB RAM**, autoscale | $80–$240 |
| **Fly.io — frontend** | 2× shared-cpu-1x, 256 MB RAM | ~$6 |
| **Fly Postgres (managed)** | dedicated-cpu-1x, 10 GB volume, daily snapshots | ~$30 |
| **Postgres replica (read)** | optional read replica for reports queries | ~$30 |
| **Upstash Redis** | Pay-as-you-go (~5M commands/month) | ~$10 |
| **SendGrid** | Essentials 100k plan | ~$15 |
| **Object storage (S3/R2)** | Static assets, JSON exports, ~10 GB | ~$2 |
| **Backup storage** | Off-site Postgres snapshots, ~30 GB retained | ~$3 |
| **GitHub Actions** | Likely Team plan to handle longer Java build/test cycles | ~$4 |
| **Monthly total (infrastructure)** | | **~$180–$340/month** |

### One-Time / Per-Run Costs (80,000 user demo)

| Service | Calculation | One-Time Cost |
|---|---|---|
| **Twilio SMS** | 80,000 verifications × $0.0079/SMS | ~$632 |
| **SendGrid** | 160,000 emails (2 per user) | ~$10 |
| **One-time demo run total** | | **~$642** |

> **Why no Irys/Arweave line item?** The Spring model uses PostgreSQL as the system of
> record. Permanent decentralized storage is *optional* in this architecture — you could
> still write encrypted snapshots to Irys for auditability, but it's no longer a hard
> dependency. If added back, the cost is the same as the Node architecture (~$0.32 per
> 80k records on Irys mainnet).

---

## Cost Summary Table

| Environment | Monthly (infrastructure) | One-Time (full run) | Total (first month) |
|---|---|---|---|
| **Local** | $0 | $0 | $0 |
| **Staging** | ~$24 | ~$8 | ~$32 |
| **Production** | ~$180–$340 | ~$642 | ~$822–$982 |

### Side-by-side with current architecture

| Tier | Node + Irys (current) | Spring + Postgres (this doc) | Delta |
|---|---|---|---|
| Local | $0 | $0 | — |
| Staging monthly | ~$9 | ~$24 | **+$15** |
| Production monthly | ~$80–$185 | ~$180–$340 | **+$100–$155** |
| Production one-time | ~$642 | ~$642 | — *(same SMS/email costs)* |

The recurring delta (~$100–$155/mo at production scale) is the price of supporting a
multi-tenant platform — JVM memory, managed Postgres, replicas, and richer build/CI.

---

## Scalability Assessment

This is the part that matters most for your "which model scales better?" question. There
are three different scalability axes, and they don't all favor the same architecture.

### Axis 1: Cost-per-response at fixed scale

**Current architecture wins.** A stateless event-loop API serving a single form is the
cheapest possible shape for a high-volume submission workload. Redis is faster than any
relational DB for the dedup keys, Irys absorbs the durable-storage cost at fractions of
a cent per record, and Node instances stay small (256–512 MB).

At 80k responses, the Node architecture costs roughly **$80–$185/mo**. Spring Boot
serving the same 80k responses against Postgres would cost **$180–$340/mo** — about
2× — because of JVM memory and database overhead.

### Axis 2: Cost of growing the product surface

**Spring architecture wins.** Adding new features to the current architecture is hard:
there is no `User` table, no concept of who can do what, no audit trail of edits, no
draft state, no approval workflow. Building any of these into the current Node
architecture would mean adding Postgres anyway and substantially refactoring submission
flow. At that point you are paying the Node→Spring cost delta *and* doing the rewrite.

If the roadmap includes any of: user accounts, multiple polls per cycle, creator
dashboards, admin moderation, response history, edit-with-audit, role-based access,
analytics across polls — the Spring architecture is the cheaper path because those
features are native to the model.

### Axis 3: Operational scalability under load spikes

**Current architecture wins.** Node containers boot in 1–3 seconds. Fly.io can scale
from 2 instances to 10 in under a minute. JVM containers boot in 10–30 seconds, so
autoscaling responds more slowly and you typically run 1–2 extra warm instances as
buffer (which costs extra). For traffic with sharp election-night spikes, the Node
shape is more responsive per dollar.

A Spring Boot deployment can mitigate this with GraalVM native-image compilation
(boots in ~100 ms, uses ~150 MB RAM). That closes the gap considerably but adds build
complexity and limits some Spring features (reflection-heavy libraries need explicit
hints).

### Scoring

| Question | Winner |
|---|---|
| "We just need to handle one big election night, lowest cost" | **Node + Irys** |
| "We want to ship 50 polls a year with creator/admin workflows" | **Spring + Postgres** |
| "We want fast autoscale on traffic spikes" | **Node + Irys** (or Spring + GraalVM) |
| "We want to add user accounts, history, and roles next quarter" | **Spring + Postgres** |
| "Decentralized immutability is a core differentiator" | **Node + Irys** (Spring can opt-in but doesn't require it) |

---

## Cost Reduction Options

### Option A — Drop Postgres replica (save ~$30/mo)
A read replica is only needed if reports queries hurt write performance. Until you
see DB CPU > 70% in steady state, skip it.

### Option B — GraalVM native image (save ~$60–$120/mo at production)
Compile the Spring backend to a native binary. Boot time drops to ~100 ms, memory
drops to ~150–250 MB, and you can run on shared-cpu-1x instances instead of
shared-cpu-2x. Pays back the engineering effort within 2–3 months at production scale.

### Option C — Move SMS verification off the critical path (save ~$632)
Same as Option A in `COSTS.md`: replace Twilio SMS with format-only validation. This
applies equally to either architecture.

### Option D — Use Supabase / Neon for Postgres (save ~$15/mo)
Free tiers cover up to ~500 MB and modest connection counts. Suitable for staging or
low-volume production. Not recommended above ~10k active users.

### Revised Production Demo Budget (Spring, all reductions applied)

| Item | Revised Cost |
|---|---|
| Fly.io backend (GraalVM, 2 small machines) | ~$30 |
| Fly.io frontend (2 machines) | ~$6 |
| Neon Postgres (free tier) | $0 |
| Upstash Redis | ~$5 |
| SendGrid | ~$15 |
| Twilio (mocked or skipped) | $0 |
| **Revised total** | **~$56 for a full demo month** |

Compared with the Node revised demo (~$45/mo), the optimized Spring stack is only
~$10/mo more — and you get the platform features for that price.

---

## Recommendation

**If the goal is the cheapest possible deployment of the current single-form
submission system**, stay on the current architecture (`COSTS.md`).

**If the roadmap includes any of: multiple polls, user accounts, creator/admin
workflows, response history, role-based access, or any of the entities described in
`docs/UML/Class-diagram.plantuml`**, migrate to Spring Boot + Postgres. The cost
delta (~$100–$155/mo at production scale, or ~$10/mo with the reductions in Option B)
is small relative to the engineering cost of bolting those features onto the current
architecture.

A pragmatic middle path also exists: keep the current Node architecture for the
single-ballot submission flow, and build the platform layer (poll creation,
user management, admin workflow) as a separate Spring Boot service that hands off
the *response collection* to the Node service. This is more complex operationally
but lets you preserve the cost profile of the high-volume path while still building
the multi-tenant features in the model that suits them best.

---

## Pricing Sources

| Service | Pricing page |
|---|---|
| Fly.io | https://fly.io/docs/about/pricing/ |
| Fly Postgres | https://fly.io/docs/postgres/managing/pricing/ |
| Upstash Redis | https://upstash.com/pricing |
| Neon Postgres | https://neon.tech/pricing |
| Supabase | https://supabase.com/pricing |
| Twilio SMS | https://www.twilio.com/en-us/sms/pricing/us |
| SendGrid | https://sendgrid.com/en-us/pricing |
| GraalVM native image | https://www.graalvm.org/native-image/ |

> Prices were last verified in early 2026 and may have changed. Always check the
> provider's current pricing page before budgeting a real deployment.
