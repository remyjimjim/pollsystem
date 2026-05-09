# Deploying Locally

Run the full stack on a developer machine: Postgres in Docker, backend from
Gradle, frontend from Vite. No cloud account required.

## Prerequisites

See `docs/BUILDING.md` — JDK 17, Gradle 8.10.2, Node 20, Docker.

## 1. Start Postgres

The repo's `docker-compose.yml` provisions a single Postgres 16 container with
the credentials the backend expects.

From the repo root:

```bash
docker compose up -d db
```

This launches:
- **Container**: `pollsystem-db`
- **Image**: `postgres:16`
- **Database**: `pollsystem`
- **User / password**: `polladmin` / `pollpass123`
- **Port**: `5432` on the host
- **Volume**: `pollsystem-data` (persists across `docker compose down`; remove with `docker compose down -v` to wipe).

Verify the DB is reachable:

```bash
docker compose ps
psql -h localhost -U polladmin -d pollsystem -c '\dt'   # password: pollpass123
```

(The `\dt` will show no tables until the backend boots and Flyway runs.)

## 2. Configure environment

The backend reads these env vars at startup. Defaults come from
`backend/src/main/resources/application.yml`:

| Variable | Default | What for |
|---|---|---|
| `SENDGRID_API_KEY` | placeholder | Outbound email (magic-link delivery, transactional). For local dev, point at Mailpit instead — see below. |
| `JWT_SECRET` | placeholder (DO NOT use in any shared env) | Signs short-lived auth tokens. Set to any 32+ char random string locally. |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/pollsystem` | Override if running Postgres elsewhere. |
| `SPRING_DATASOURCE_USERNAME` | `polladmin` | DB user. |
| `SPRING_DATASOURCE_PASSWORD` | `pollpass123` | DB password. |

A minimal local `.env` (loaded however you prefer — `direnv`, `dotenv-cli`, or
exported in your shell):

```bash
export JWT_SECRET="$(openssl rand -hex 32)"
export SENDGRID_API_KEY="local-dev-not-real"
```

### Optional: catch outbound mail with Mailpit

Mail goes to SendGrid's SMTP host by default, which fails locally without a
real key. The cleanest local setup is to run **Mailpit** as a fake SMTP sink:

```bash
docker run -d --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
```

Then override the mail host for local runs (in your shell or a local
`application-local.yml`):

```bash
export SPRING_MAIL_HOST=localhost
export SPRING_MAIL_PORT=1025
export SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=false
export SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false
```

Mailpit's web UI is at http://localhost:8025 — every email the backend would
have sent shows up there with clickable magic-link URLs.

## 3. Run the backend

From `backend/`:

```bash
gradle bootRun
```

What happens on first boot:
- Spring connects to Postgres at `localhost:5432`.
- Flyway applies `V1__initial_schema.sql` through `V6__ip_rules.sql` in order.
- Tomcat starts on port `8080`.

You should see `Started PollSystemApplicationKt in N seconds` in the logs.

Verify:

```bash
curl http://localhost:8080/actuator/health   # if actuator is enabled
curl http://localhost:8080/api/...           # any endpoint
```

## 4. Run the frontend

From `frontend/` in a second terminal:

```bash
npm install      # first time only
npm run dev
```

Vite serves on `http://localhost:3000` and proxies `/api/*` to the backend on
`localhost:8080` (configured in `vite.config.ts`).

Open `http://localhost:3000` in a browser.

## 5. Stripe webhooks (when working on the paid flow)

For the Substack/Stripe subscription path you'll want real Stripe webhook
events delivered to your local backend. The Stripe CLI handles this without
exposing your machine to the internet:

```bash
stripe login
stripe listen --forward-to localhost:8080/webhooks/stripe
```

The CLI prints a webhook signing secret on first run. Export it as
`STRIPE_WEBHOOK_SECRET` so the backend can verify signatures. Use **test-mode
keys only** locally — no real charges happen.

## Tearing down

```bash
docker compose down            # stops Postgres, keeps the volume
docker compose down -v         # also wipes the database
docker stop mailpit && docker rm mailpit
```

Backend and frontend run in your terminals — Ctrl-C stops them.

## Common issues

| Symptom | Likely cause |
|---|---|
| `Connection refused` on backend boot | Postgres container not running (`docker compose ps`). |
| Flyway `MigrationCheckSumMismatch` | A committed migration was edited after first apply. Resolve via `flyway repair` or wipe with `docker compose down -v`. |
| `JWT_SECRET` warning at boot | `JWT_SECRET` env var not set; backend fell back to the placeholder. Set it before starting the server. |
| Frontend `/api` calls return 404 | Backend not running on `localhost:8080`, or vite proxy not picking up the config. Check `vite.config.ts`. |
| Mail attempts fail with `AuthenticationFailedException` | `SENDGRID_API_KEY` is the placeholder string; either set a real key or point at Mailpit (see step 2). |
