# Deploying to Fly.io

Backend (Spring Boot on Fly) + frontend (static Vue bundle — Cloudflare Pages,
see `DEPLOYING-CLOUDFLARE-PAGES.md`). The backend uses a **Neon Postgres**
database and **Resend** for email, both outside Fly.

> **Redis/Upstash is NOT currently used.** `docs/COSTS.md` lists it as part of the
> intended stateless-at-scale architecture, but the app is stateless-JWT +
> Postgres-backed magic-link tokens + an in-process role cache — it never calls
> Redis. Add it only when you scale to multiple backend instances (shared cache /
> rate-limiting). Skip provisioning it for launch.

```
              ┌────────────────────┐
   user ──▶   │  frontend (Pages)  │
              │  static Vue bundle  │
              └─────────┬──────────┘
                        │ /api proxy
                        ▼
              ┌────────────────────┐         ┌──────────────────┐
              │  Fly app (backend) │ ───▶   │ Neon Postgres     │
              │  Spring Boot / JVM │         └──────────────────┘
              └─────────┬──────────┘
                        │ SMTP                ┌──────────────────┐
                        ├───────────────▶   │ Resend (email)    │
                        │                    └──────────────────┘
                        │ webhook ◀── Stripe (subscription events)
```

## Prerequisites

- A Fly.io account and `flyctl` installed (`curl -L https://fly.io/install.sh | sh`).
- A Neon Postgres project (Launch plan recommended; Free works for very small staging — see `docs/COSTS.md` Option C).
- A Resend account with a verified sending domain + API key (free tier is plenty). See `### Resend` below.
- *(Optional)* A Stripe account — only needed to enable **paid creator onboarding**.
  The app is webhook-only (reads no Stripe API key), so you can deploy and run the
  whole app without Stripe; add `STRIPE_WEBHOOK_SECRET` later (Part 3).
- (No Redis needed — see the note above.)
- A logged-in `flyctl` session: `flyctl auth login`.

---

## Part 1 — Provision external services

### Neon Postgres

Nothing to set up *inside* the database — Flyway (`V1..V18`) builds the whole
schema on the backend's first boot. You just need a connection string.

1. Create a project in the Neon console.
2. **For launch, copy the DIRECT connection string** (the host *without* `-pooler`).
   Flyway takes session-level advisory locks that Neon's transaction-mode pooler
   breaks, so migrations must run on the direct endpoint. At one warm machine the
   connection count is tiny, so direct is plenty.
3. Save it for the secrets step below as `DATABASE_URL`.
4. Verify locally:

   ```bash
   psql "$DATABASE_URL" -c '\l'
   ```

> **Scaling later:** when you autoscale and want the runtime pool on Neon's
> **pooled** endpoint, point the *datasource* at the pooled host **and** set
> `SPRING_FLYWAY_URL` / `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` to the
> **direct** endpoint, so migrations still bypass the pooler. See the Flyway note
> in `application.yml`.

### Upstash Redis — skip (not used)

The app doesn't use Redis (see the note at the top). Nothing to provision.

### Resend (email)

1. Sign up at resend.com (free tier: 3,000 emails/mo, 100/day).
2. **Add + verify the sending domain** — a subdomain like `contact.surveysays.buzz`
   is recommended (keeps transactional-mail DNS separate from the root domain).
   Resend gives you SPF, DKIM, and a return-path (MX) record — add them in Porkbun's
   DNS panel. Leave Resend's optional inbound "Email Receiving" **off**: it adds an
   inbound MX we don't need, and enabling it can leave the domain stuck "partially
   verified." Without domain verification you can only send from
   `onboarding@resend.dev` (test only), and magic links would land in spam.
3. Create an **API key**.
4. Save it as `RESEND_API_KEY` — `MailConfig`'s non-local sender defaults to
   Resend's SMTP (`smtp.resend.com:587`, user `resend`, password = this key).
5. Set **`MAIL_FROM`** to a From address at your verified domain (e.g.
   `login@contact.surveysays.buzz`) — Resend rejects sends from an unverified
   address, and the From must sit on the exact domain you verified (the subdomain,
   not the root).

### Stripe

1. Create a product (e.g. "Creator Subscription, $25/mo") in the Stripe dashboard.
2. Copy the secret key (`sk_test_…` for staging) as `STRIPE_SECRET_KEY`.
3. The webhook signing secret is created in **Part 3** once the backend is live.

---

## Part 2 — Backend app

The backend now ships with `backend/Dockerfile`, `backend/.dockerignore`, and
`backend/fly.toml` (added 2026-07-22) — no `flyctl launch` scaffolding needed.

- **`Dockerfile`** — two-stage: compiles the Spring Boot fat JAR with the repo's
  pinned Gradle **wrapper** (`./gradlew bootJar`; the `eclipse-temurin` base has no
  system `gradle`), then runs it on a JRE as a non-root user. Conservative-baseline
  path — plain JVM, ~1–2 GB RAM per instance. The GraalVM native-image variant is at
  the bottom of this doc.
- **`fly.toml`** — `internal_port = 8080`, `force_https`, auto-stop/-start with one
  machine kept warm, a `shared / 2 CPU / 2048 MB` VM, `SPRING_PROFILES_ACTIVE = "prod"`
  in `[env]`, and an `/actuator/health` HTTP check. (`spring-boot-starter-actuator`
  **is** now a dependency and `application.yml` exposes `health` — the older "add
  actuator" caveat no longer applies.)

Review `backend/fly.toml` and adjust the app name / region if you aren't using
`pollsystem-backend` / `iad`. The image is verified to build with a local
`docker build`; the first `flyctl deploy` just pushes it.

### Set secrets

```bash
flyctl secrets set \
  -a pollsystem-backend \
  SPRING_DATASOURCE_URL="jdbc:postgresql://<neon-direct-host>/neondb?sslmode=require" \
  SPRING_DATASOURCE_USERNAME=neondb_owner \
  SPRING_DATASOURCE_PASSWORD="$NEON_PASSWORD" \
  JWT_SECRET="$(openssl rand -hex 32)" \
  RESEND_API_KEY="$RESEND_API_KEY" \
  MAIL_FROM="login@contact.surveysays.buzz" \
  APP_BASE_URL="https://pollsystem.pages.dev"
```

- **`SPRING_DATASOURCE_URL` must be a JDBC URL** — `jdbc:postgresql://<host>/<db>?sslmode=require`,
  not the raw `postgresql://…` Neon gives you. Use the **direct** host (no `-pooler`) and
  drop the inline `user:pass@` (they go in the USERNAME/PASSWORD secrets).
- **`APP_BASE_URL`** is where magic-link emails point — set it to the live frontend
  (`https://pollsystem.pages.dev` now; the custom domain once attached).
- **No Stripe secret.** The integration is webhook-only (no Stripe SDK, no API key read).
  Only when you enable paid onboarding do you set `STRIPE_WEBHOOK_SECRET` — see Part 3.

### Deploy

```bash
flyctl deploy -a pollsystem-backend
```

First deploy takes ~5–8 minutes (image build + push + machine boot). On
success:

```bash
flyctl status -a pollsystem-backend
flyctl logs   -a pollsystem-backend
curl https://pollsystem-backend.fly.dev/actuator/health
```

Flyway runs the migrations against Neon on first startup. Watch the logs to
confirm `Migrating schema "public" to version "6 - ip rules"`.

---

## Part 3 — Stripe webhook

The Stripe webhook needs a stable URL on the backend. After Part 2:

```bash
stripe webhooks create \
  --url https://pollsystem-backend.fly.dev/webhooks/stripe \
  --enabled-events checkout.session.completed \
  --enabled-events customer.subscription.updated \
  --enabled-events customer.subscription.deleted \
  --enabled-events invoice.paid \
  --enabled-events invoice.payment_failed
```

Stripe returns a signing secret (`whsec_…`). Set it on the backend:

```bash
flyctl secrets set -a pollsystem-backend STRIPE_WEBHOOK_SECRET="$WHSEC"
```

The backend redeploys automatically when secrets change.

---

## Part 4 — Frontend app

The frontend is a static SPA. The easiest way to host it on Fly is a tiny nginx
image that serves `dist/`.

### Add a Dockerfile (one-time)

Create `frontend/Dockerfile`:

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

And `frontend/nginx.conf`:

```nginx
server {
  listen 80;
  root /usr/share/nginx/html;
  index index.html;

  # SPA fallback — any unknown route returns index.html so vue-router can resolve it client-side.
  location / {
    try_files $uri $uri/ /index.html;
  }

  # Forward /api/* to the backend Fly app over the internal Wireguard mesh.
  location /api/ {
    proxy_pass https://pollsystem-backend.fly.dev/api/;
    proxy_set_header Host pollsystem-backend.fly.dev;
  }
}
```

### Launch and deploy

```bash
cd frontend
flyctl launch --no-deploy --name pollsystem-frontend --region iad
# Edit fly.toml: set internal_port = 80, memory_mb = 256, cpus = 1, cpu_kind = shared
flyctl deploy -a pollsystem-frontend
```

Open `https://pollsystem-frontend.fly.dev` — the SPA loads, and `/api` calls
hit the backend.

---

## Part 5 — Custom domain

```bash
flyctl certs add poll.example.com           -a pollsystem-frontend
flyctl certs add api.poll.example.com       -a pollsystem-backend
```

Add the DNS records Fly prints. Once the certs are issued (a few minutes):

- `https://poll.example.com` serves the frontend.
- `https://api.poll.example.com` serves the backend.
- Update the nginx `proxy_pass` to point at `api.poll.example.com` and Stripe webhook URL to use the same. Redeploy both apps.

---

## GraalVM native-image variant (the cost lever)

`docs/COSTS.md` describes how GraalVM cuts backend RAM 4× and saves $45–$95/month
at staging scale. The build.gradle.kts doesn't enable it yet. To turn it on:

1. Add to `backend/build.gradle.kts` plugins block:

   ```kotlin
   id("org.graalvm.buildtools.native") version "0.10.3"
   ```

2. Replace `backend/Dockerfile` with a multi-stage GraalVM build:

   ```dockerfile
   FROM ghcr.io/graalvm/graalvm-community:17 AS build
   WORKDIR /app
   COPY . .
   RUN --mount=type=cache,target=/root/.gradle \
       gradle nativeCompile -x test --no-daemon

   FROM debian:bookworm-slim
   COPY --from=build /app/build/native/nativeCompile/pollsystem /app/pollsystem
   EXPOSE 8080
   ENTRYPOINT ["/app/pollsystem"]
   ```

3. Update `fly.toml`:

   ```toml
   [[vm]]
     cpu_kind = "shared"
     cpus = 1
     memory_mb = 512
   ```

4. Deploy. The native-image build takes 5–10 minutes (vs 1–2 for the JVM path),
   but the resulting machine boots in ~100 ms instead of 10–30 s.

Caveats:
- Reflection-heavy libraries may need explicit hints in `META-INF/native-image/`. Spring Boot 3 generates most of these via its AOT engine, but a few dependencies (notably some validation and JSON adapters) may need manual entries — Spring's `--enable-preview` AOT processing usually catches these and the build will fail loudly with what's missing.
- Local debugging is harder; keep the JVM Dockerfile around for `flyctl deploy --build-arg JVM=true` style overrides if you need it.

---

## Operational runbook

| Task | Command |
|---|---|
| Tail backend logs | `flyctl logs -a pollsystem-backend` |
| SSH into a backend machine | `flyctl ssh console -a pollsystem-backend` |
| Run a one-off DB query | `psql "$DATABASE_URL"` (Neon connection string) |
| Manually scale backend | `flyctl scale count 3 -a pollsystem-backend` |
| Roll back a bad deploy | `flyctl releases -a pollsystem-backend` then `flyctl deploy --image <previous>` |
| Rotate JWT secret | `flyctl secrets set -a pollsystem-backend JWT_SECRET="$(openssl rand -hex 32)"` (invalidates all sessions) |

---

## What this doc does **not** cover

- High-availability Postgres failover (production-tier concern; Neon Scale or Fly Postgres HA).
- Daily Postgres backups to off-site object storage (Neon includes PITR; for HIPAA/SOC2 compliance you'd add an external backup target).
- Monitoring/alerting (Fly metrics + Grafana Cloud free tier is the natural starting point).
- Multi-region failover.

These are intentionally left out — they belong to a "leaving lowest-cost staging" follow-up, per `docs/COSTS.md`.

## Test / staging environments

We run **local + production**; a test site is a separate *environment* (a free
subdomain like `staging.surveysays.buzz`), never a separate domain. The preferred
free testing paths — local, Cloudflare Pages preview URLs, and the test-mode-first
gate — plus the optional always-on staging subdomain (second Fly app + Neon
branch) are all in **`docs/ENVIRONMENTS.md`**.
