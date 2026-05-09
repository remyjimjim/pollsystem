# Deploying to Fly.io

Two Fly apps — one for the backend (Spring Boot) and one for the frontend
(static Vue/Vite bundle). They share a database (Neon) and a Redis (Upstash)
that live outside Fly. This is the lowest-cost-path topology described in
`docs/COSTS.md`.

```
              ┌────────────────────┐
   user ──▶   │  Fly app (frontend) │
              │  static Vue bundle  │
              └─────────┬──────────┘
                        │ /api proxy or fetch
                        ▼
              ┌────────────────────┐         ┌──────────────────┐
              │  Fly app (backend) │ ───▶   │ Neon Postgres     │
              │  Spring Boot       │         └──────────────────┘
              │  Java 17 JVM        │         ┌──────────────────┐
              └─────────┬──────────┘ ───▶   │ Upstash Redis     │
                        │                    └──────────────────┘
                        │ SMTP                ┌──────────────────┐
                        ├───────────────▶   │ SendGrid          │
                        │                    └──────────────────┘
                        │ webhook ◀── Stripe (subscription events)
```

## Prerequisites

- A Fly.io account and `flyctl` installed (`curl -L https://fly.io/install.sh | sh`).
- A Neon Postgres project (Launch plan recommended; Free works for very small staging — see `docs/COSTS.md` Option C).
- An Upstash Redis database (pay-as-you-go).
- A SendGrid API key (free tier is sufficient).
- A Stripe account in test mode (live mode for production).
- A logged-in `flyctl` session: `flyctl auth login`.

---

## Part 1 — Provision external services

### Neon Postgres

1. Create a project in the Neon console.
2. Copy the **Pooled connection string** (port 5432 with `-pooler` in the host) — Spring's connection pool plus Neon's pooler avoids over-subscribing connections during autoscale.
3. Save it for step 3 below as `DATABASE_URL`.
4. Verify locally:

   ```bash
   psql "$DATABASE_URL" -c '\l'
   ```

### Upstash Redis

1. Create a Redis database in the Upstash console (US region closest to your Fly primary region).
2. Copy the **TLS connection URL** (`rediss://…`).
3. Save it as `REDIS_URL`.

### SendGrid

1. Create an API key with the **Mail Send** scope only.
2. Verify the sender domain (or single-sender) so magic-link emails don't end up in spam.
3. Save the key as `SENDGRID_API_KEY`.

### Stripe

1. Create a product (e.g. "Creator Subscription, $25/mo") in the Stripe dashboard.
2. Copy the secret key (`sk_test_…` for staging) as `STRIPE_SECRET_KEY`.
3. The webhook signing secret is created in **Part 3** once the backend is live.

---

## Part 2 — Backend app

The backend currently has no `Dockerfile` or `fly.toml`. Both can be generated
by `flyctl launch`, but Spring Boot apps run cleaner with an explicit Dockerfile.

### Add a Dockerfile (one-time)

Create `backend/Dockerfile`:

```dockerfile
# Build stage — compile to a Spring Boot fat JAR.
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    gradle bootJar -x test --no-daemon

# Runtime stage — JRE only, smaller image.
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/pollsystem-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
```

This is the **conservative-baseline** path — runs on the JVM, expects ~1 GB RAM
per instance. The GraalVM native-image variant is at the bottom of this doc.

### Launch the app

From `backend/`:

```bash
flyctl launch --no-deploy --name civicchain-backend --region iad
```

Answer "no" when asked to provision Postgres or Redis (we're using Neon and Upstash externally). The command writes a `fly.toml`. Edit it:

```toml
app = "civicchain-backend"
primary_region = "iad"

[build]
  dockerfile = "Dockerfile"

[http_service]
  internal_port = 8080
  force_https = true
  auto_stop_machines = true        # auto-stop when idle (saves $)
  auto_start_machines = true
  min_machines_running = 1         # keep at least one warm
  processes = ["app"]

[[vm]]
  cpu_kind = "shared"
  cpus = 2
  memory_mb = 2048                 # 1024 is tight; 2048 is comfortable on JVM

[[http_service.checks]]
  grace_period = "30s"             # JVM cold-start headroom
  interval = "15s"
  timeout = "2s"
  method = "GET"
  path = "/actuator/health"        # enable spring-boot-starter-actuator
```

> **Note**: the project doesn't currently include `spring-boot-starter-actuator`. Either add it (`implementation("org.springframework.boot:spring-boot-starter-actuator")` in `build.gradle.kts`) and expose `/actuator/health`, or change the health-check path to any cheap GET endpoint that returns 200.

### Set secrets

```bash
flyctl secrets set \
  -a civicchain-backend \
  SPRING_DATASOURCE_URL="$DATABASE_URL" \
  SPRING_DATASOURCE_USERNAME=neondb_owner \
  SPRING_DATASOURCE_PASSWORD="$NEON_PASSWORD" \
  REDIS_URL="$REDIS_URL" \
  JWT_SECRET="$(openssl rand -hex 32)" \
  SENDGRID_API_KEY="$SENDGRID_API_KEY" \
  STRIPE_SECRET_KEY="$STRIPE_SECRET_KEY"
```

(Stripe webhook secret comes in Part 3.)

### Deploy

```bash
flyctl deploy -a civicchain-backend
```

First deploy takes ~5–8 minutes (image build + push + machine boot). On
success:

```bash
flyctl status -a civicchain-backend
flyctl logs   -a civicchain-backend
curl https://civicchain-backend.fly.dev/actuator/health
```

Flyway runs the migrations against Neon on first startup. Watch the logs to
confirm `Migrating schema "public" to version "6 - ip rules"`.

---

## Part 3 — Stripe webhook

The Stripe webhook needs a stable URL on the backend. After Part 2:

```bash
stripe webhooks create \
  --url https://civicchain-backend.fly.dev/webhooks/stripe \
  --enabled-events checkout.session.completed \
  --enabled-events customer.subscription.updated \
  --enabled-events customer.subscription.deleted \
  --enabled-events invoice.paid \
  --enabled-events invoice.payment_failed
```

Stripe returns a signing secret (`whsec_…`). Set it on the backend:

```bash
flyctl secrets set -a civicchain-backend STRIPE_WEBHOOK_SECRET="$WHSEC"
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
    proxy_pass https://civicchain-backend.fly.dev/api/;
    proxy_set_header Host civicchain-backend.fly.dev;
  }
}
```

### Launch and deploy

```bash
cd frontend
flyctl launch --no-deploy --name civicchain-frontend --region iad
# Edit fly.toml: set internal_port = 80, memory_mb = 256, cpus = 1, cpu_kind = shared
flyctl deploy -a civicchain-frontend
```

Open `https://civicchain-frontend.fly.dev` — the SPA loads, and `/api` calls
hit the backend.

---

## Part 5 — Custom domain

```bash
flyctl certs add poll.example.com           -a civicchain-frontend
flyctl certs add api.poll.example.com       -a civicchain-backend
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
| Tail backend logs | `flyctl logs -a civicchain-backend` |
| SSH into a backend machine | `flyctl ssh console -a civicchain-backend` |
| Run a one-off DB query | `psql "$DATABASE_URL"` (Neon connection string) |
| Manually scale backend | `flyctl scale count 3 -a civicchain-backend` |
| Roll back a bad deploy | `flyctl releases -a civicchain-backend` then `flyctl deploy --image <previous>` |
| Rotate JWT secret | `flyctl secrets set -a civicchain-backend JWT_SECRET="$(openssl rand -hex 32)"` (invalidates all sessions) |

---

## What this doc does **not** cover

- High-availability Postgres failover (production-tier concern; Neon Scale or Fly Postgres HA).
- Daily Postgres backups to off-site object storage (Neon includes PITR; for HIPAA/SOC2 compliance you'd add an external backup target).
- Monitoring/alerting (Fly metrics + Grafana Cloud free tier is the natural starting point).
- Multi-region failover.

These are intentionally left out — they belong to a "leaving lowest-cost staging" follow-up, per `docs/COSTS.md`.
