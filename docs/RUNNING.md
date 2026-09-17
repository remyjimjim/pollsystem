# Running pollsystem locally — a bash cheat-sheet

> Commands and processes that are good to know and come up fairly frequently:
> starting the stack, hot reload, entering containers, tailing logs, where the
> creds live, "port in use" fixes, querying the DB, running tests, etc. A
> shortened, practical companion to `DEPLOYING-LOCAL.md`; meant to evolve.

---

## The launcher — `./scripts/BuildAndDeploy.bash`

One script drives local dev and the staging deploy. `local` is the default
(`./scripts/BuildAndDeploy.bash` with no argument == `local`).

| Command | What it does |
|---|---|
| `./scripts/BuildAndDeploy.bash local` | Full stack on the **host**: brings up the Postgres + Mailpit containers, then runs the backend (`bootRun`), a **Kotlin watcher**, and **Vite** — with **backend + frontend hot-reload on save**. Ctrl-C stops the app processes (containers stay up). |
| `./scripts/BuildAndDeploy.bash local-docker` | Full stack **in Docker** (compose `app` profile) — backend + frontend containers too, no host JDK/Node needed. Frontend hot-reloads (Vite HMR); the backend runs the **built JAR**, so backend code changes need an image rebuild (`down` then `local-docker` again, or `docker compose --profile app up -d --build backend`). |
| `./scripts/BuildAndDeploy.bash infra` | Just the infra containers (Postgres + Mailpit), no app. |
| `./scripts/BuildAndDeploy.bash status` | Show local container status. |
| `./scripts/BuildAndDeploy.bash down` | Stop & remove the containers (keeps the DB data volume; `docker compose down -v` to wipe it). |
| `./scripts/BuildAndDeploy.bash test` | Build + deploy to the Fly **staging** environment (`docs/DEPLOYING-FLY.md`). |
| `./scripts/BuildAndDeploy.bash test-secrets` | (Re)import staging secrets into Fly from the OS keychain. |

**`local` env flags:** `SKIP_BACK=1` (frontend only), `SKIP_FRONT=1` (backend
only), `SKIP_WATCH=1` (no Kotlin watcher → disables backend hot reload),
`JWT_SECRET=…` (otherwise a throwaway one is generated per run).

**Which mode?** Use `local` for day-to-day dev (fastest backend restarts).
Use `local-docker` to run the whole thing in containers (integration checks, or
a machine without a JDK/Node).

### Hot reload — how it works
- **Frontend:** Vite HMR. Save a `.vue`/`.ts`/`.css` → the browser updates
  instantly. (In `local-docker` the container watches via polling.)
- **Backend (`local` only):** the Kotlin watcher (`./gradlew -t classes`)
  recompiles on save; spring-boot-devtools (a `developmentOnly` dep) sees the
  fresh classes and restarts the Spring context in ~2–3s — new `@RestController`
  routes and any backend change are picked up without rerunning `bootRun`.

### Manual equivalent (what `local` automates)
Three terminals, if you'd rather run the pieces yourself:
- **Backend** (from `backend/`): `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`
- **Kotlin watcher** (from `backend/`): `./gradlew -t classes`
- **Frontend** (from `frontend/`): `npm run dev`
(First bring up infra: `./scripts/BuildAndDeploy.bash infra`.)

---

## Payments in local dev

**Both `local` and `local-docker` mock Stripe by default** — no keys, no
webhooks, no network. Register → **Continue to payment** → you land on the
success page → open **Mailpit** (<http://localhost:8025>) → click the sign-in
link → you're in as an active paid member. Renew ("Manage/Renew") is simulated
the same way. See `payment/MockPaymentProvider`.

- `local-docker` sets `APP_PAYMENTS_PROVIDER=mock` on the backend service in
  `docker-compose.yml`; `local` defaults it to `mock` in `BuildAndDeploy.bash`.
- **To test *real* Stripe test-mode instead**, set
  `APP_PAYMENTS_PROVIDER=stripe`, provide `STRIPE_API_KEY` / `STRIPE_PRICE_ID`
  (`sk_test_…` / `price_…`), and forward webhooks with
  `stripe listen --forward-to localhost:8080/webhooks/stripe`
  (full steps in `docs/STRIPE-TEST-RUNBOOK.md`). On the host that's
  `APP_PAYMENTS_PROVIDER=stripe ./scripts/BuildAndDeploy.bash local`.

The mock provider is registered **only** when `app.payments.provider=mock`, so
staging/prod (which never set it) always use real Stripe.

---

## Entering the containers

Container names exist while the relevant services are up. The db + mailpit
containers run in **all** modes; the `backend`/`frontend` containers exist only
under **`local-docker`** (in `local` those run on the host — nothing to exec).

| Container | Enter it | Notes |
|---|---|---|
| Backend | `docker exec -it pollsystem-backend bash` | Runtime image: user `spring`, `/app` has `app.jar` + `logs` only — **no gradle/source** (inspect + logs, not build). |
| Frontend | `docker exec -it pollsystem-frontend bash` | Vite dev server: user `root`, Node 22, source bind-mounted at `/app`. |
| Postgres | `docker exec -it pollsystem-db psql -U polladmin -d pollsystem` | Opens a psql shell (see next section). |
| Mailpit | no shell needed | Captured email UI: <http://localhost:8025> |

One-off command without a shell, e.g. tail the frontend's Vite output:
`docker logs -f pollsystem-frontend`

---

## Accessing / querying the DB

Creds live in `docker-compose.yml` (`polladmin` / `pollpass123`, db `pollsystem`).

**From the host (via the db container — no local psql needed):**
- Is it running? `docker ps --filter name=pollsystem-db`
- One-off query: `docker exec -it pollsystem-db psql -U polladmin -d pollsystem -c "SELECT count(*) FROM users;"`
- Interactive shell: `docker exec -it pollsystem-db psql -U polladmin -d pollsystem`

**With a local psql client (port 5432 is published to the host):**
- `PGPASSWORD=pollpass123 psql -h localhost -U polladmin -d pollsystem`

**From Docker Desktop:** open the `pollsystem-db` container → **Exec** tab →
`psql -U polladmin -d pollsystem`.

---

## Logs

- **Containerized (`local-docker`):** `docker compose --profile app logs -f backend`
  (or `frontend` / `db` / `mailpit`); or `docker logs -f pollsystem-backend`.
- **Host (`local`):** backend + watcher + Vite all stream to the terminal
  running the script.
- **Backend file logs** (`combined.log` / `error.log` under `logs/`) are written
  only under the **prod** profile; local/`local-docker` log to the console
  (stdout), so use `docker logs` / the script terminal.

---

## Running tests

- **Backend (host — required):** `cd backend && ./gradlew test`
  Uses Testcontainers, which spins up a throwaway Postgres via the host's Docker,
  so it must run on the host (the runtime container has no JDK/gradle/Docker
  socket). Single class: `./gradlew test --tests "org.kodewerks.pollsystem.auth.*"`.
- **Frontend unit (Vitest):** in the container
  `docker exec -it pollsystem-frontend npm test`, or on the host
  `cd frontend && npm test`. Type-check: `cd frontend && npm run type-check`.
- **Playwright e2e:** `cd frontend && npx playwright test register-colorado-users --headed`

---

## "Port :xxxx already in use"

Find and kill whatever holds the port (e.g. a stray `bootRun` or Vite):
```
sudo lsof -t -i:3000        # or :8080, :5432, :1025
sudo kill -9 <pid>          # pid from the lsof output
```
Or, if it's a container: `./scripts/BuildAndDeploy.bash down`.
