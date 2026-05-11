# Deploying Locally

Run the full stack on a developer machine: Postgres in Docker, backend from
Gradle, frontend from Vite, Mailpit for captured magic-link emails.
No cloud account required.

## Prerequisites

See `docs/BUILDING.md` — JDK 17 (the Gradle toolchain auto-provisions if absent),
Node 20, Docker.

## 1. Start Postgres and Mailpit

The repo's `docker-compose.yml` provisions Postgres. Mailpit runs from a
plain `docker run` (it's not yet in the compose file).

From the repo root:

```bash
docker compose up -d db
docker run -d --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
```

This launches:
- **Postgres** — container `pollsystem-db`, database `pollsystem`,
  user/password `polladmin` / `pollpass123`, host port `5432`,
  persistent volume `pollsystem-data`.
- **Mailpit** — fake SMTP on `localhost:1025`, web UI at `http://localhost:8025`.

Verify both are up:

```bash
docker compose ps
docker ps --filter name=mailpit --format '{{.Names}} {{.Status}}'
```

## 2. Configure environment

The backend reads these env vars at startup. Defaults come from
`backend/src/main/resources/application.yml` and (when the `local` profile is
active) `application-local.yml`:

| Variable | Default | What for |
|---|---|---|
| `JWT_SECRET` | placeholder (DO NOT use in shared env) | Signs short-lived auth tokens. Set to any 32+ char random string locally. |
| `SENDGRID_API_KEY` | placeholder | Outbound email when **not** on the `local` profile. The `local` profile points mail at Mailpit instead and ignores this. |
| `STRIPE_WEBHOOK_SECRET` | empty | Optional; only needed if you run the Stripe CLI listener (step 5). |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/pollsystem` | Override if Postgres runs elsewhere. |
| `SPRING_DATASOURCE_USERNAME` | `polladmin` | DB user. |
| `SPRING_DATASOURCE_PASSWORD` | `pollpass123` | DB password. |

A minimal local `.env` (load via `direnv`, `dotenv-cli`, or `export` in your shell):

```bash
export JAVA_HOME=/path/to/jdk-17           # Gradle 8.10.2 needs a JDK 8–23 launcher
export PATH="$JAVA_HOME/bin:$PATH"
export JWT_SECRET="$(openssl rand -hex 32)"
```

## 3. Run the backend with the `local` profile

The `local` Spring profile (in `application-local.yml`) points outbound mail
at Mailpit and sets the magic-link base URL to the Vite dev server. **Always
use it for local development.** Without it, magic-link emails are sent to
SendGrid's SMTP host with a placeholder API key — they silently fail.

From `backend/`:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

What happens on first boot:
- Spring connects to Postgres at `localhost:5432`.
- Flyway applies `V1__initial_schema.sql` through `V7__magic_link_and_stripe.sql` in order.
- Tomcat starts on port `8080`.
- Mail is wired to Mailpit (`localhost:1025`).

You should see:

```
The following 1 profile is active: "local"
Started PollSystemApplicationKt in N seconds
```

Quick sanity check:

```bash
curl http://localhost:8080/api/poll-types   # returns the seeded Election/Questionnaire/Referendum
```

## 4. Run the frontend

From `frontend/` in a second terminal:

```bash
npm install      # first time only
npm run dev
```

Vite serves on `http://localhost:3000` and proxies `/api/*` to the backend on
`localhost:8080` (configured in `vite.config.ts`). Open
`http://localhost:3000` in a browser.

## 5. Smoke test: first sign-in via magic link

With backend + frontend + Mailpit all running:

1. Browse to `http://localhost:3000/register`.
2. Enter an email, phone (e.g. `5551234567`), and zipcode (`90001`). Click
   **"Email me a sign-in link"**. The page should switch to a "Check your
   email" confirmation.
3. Open `http://localhost:8025` (Mailpit's web UI). You'll see an email
   titled **"Your sign-in link"** addressed to the email you entered.
4. Click the link in the Mailpit message — it sends you to
   `http://localhost:3000/auth/magic-link?token=…`. The view redeems the
   token, stores a JWT in `localStorage`, and redirects to `/`. You're now
   authenticated as a `USER`.

For subsequent sign-ins, hit `/login`, enter just the email — same flow,
without re-typing phone/zipcode.

Token rules:
- 15-minute expiry (configurable via `app.magic-link.ttl-minutes`).
- One-shot — once redeemed, the same link returns 401.
- Storage: only the SHA-256 hash is persisted in `magic_link_tokens`. The
  raw token only lives in the email Mailpit captured.

## 6. Stripe webhooks (optional, for the paid flow)

For the Substack/Stripe subscription path you'll want real Stripe webhook
events delivered to your local backend. The Stripe CLI tunnels them in
without exposing your machine to the internet:

```bash
stripe login
stripe listen --forward-to localhost:8080/webhooks/stripe
```

The CLI prints a webhook signing secret on first run. Export it as
`STRIPE_WEBHOOK_SECRET` and restart the backend so it can verify signatures.
Use **test-mode keys only** locally — no real charges happen.

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
| `BUILD FAILED ... 25.0.2` from `./gradlew` | Gradle 8.10.2 won't run on JDK 24+. Set `JAVA_HOME` to a JDK 8–23. |
| Flyway `MigrationCheckSumMismatch` | A committed migration was edited after first apply. Resolve via `flyway repair` or wipe with `docker compose down -v`. |
| Magic-link email never shows up in Mailpit | Backend not started with `SPRING_PROFILES_ACTIVE=local`. Restart with the profile and confirm the boot log says `The following 1 profile is active: "local"`. |
| Frontend register/login shows "Registration failed" | Backend running but stale (predates the magic-link migration). Rebuild and restart with the `local` profile. |
| Frontend `/api` calls return 404 | Backend not running on `localhost:8080`, or vite proxy misconfigured. Check `vite.config.ts`. |
| `JWT_SECRET` warning at boot | Env var not set; backend fell back to the placeholder. Set it before starting the server. |
