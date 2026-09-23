# Running pollsystem fully dockerized — the Dev Container workflow

> For working **entirely inside a container** — no JDK/Node/flyctl on your host,
> just Docker Desktop + VS Code. You edit and run everything from a **VS Code Dev
> Container** (`.devcontainer/`, see its `README.md`); VS Code's window stays on
> the host, but its terminals, extensions, `gradle`/`npm`, `git`, and **Claude
> Code** all run in the container.
>
> Prefer running on the host (JDK + Node installed)? See **`docs/RUNNING-on-host.md`**.

---

## The everyday flow

```
cd <repo> && code .
```
1. Command Palette (**F1** / **Ctrl+Shift+P**) → **"Dev Containers: Reopen in
   Container"**. VS Code reloads *inside* the container (first build pulls the
   base image + Features — a few minutes; later opens are fast).
2. Open a terminal (**Ctrl+`**) → **`claude -c`** to resume the most recent Claude
   Code conversation (your `~/.claude` is mounted, so it's the same history as on
   the host).
3. Drive everything through the launcher — all of these work **from inside**:
   ```bash
   ./scripts/BuildAndDeploy.bash local-docker    # build + run the whole stack in containers
   ./scripts/BuildAndDeploy.bash down            # stop it
   ./scripts/BuildAndDeploy.bash test            # deploy to Fly staging
   ./scripts/BuildAndDeploy.bash test-secrets    # (re)import staging secrets to Fly
   cd backend && ./gradlew test                  # unit tests (Testcontainers)
   ```

**Open the app in your *host* browser at <http://localhost:3000>** (and the
backend at `:8080`, Mailpit UI at `:8025`). The containers' ports publish to the
host, so you browse from the host, not from inside the Dev Container.

> Why `local-docker` and not `local` here? `local` runs `bootRun`/Vite as
> processes and expects the DB at `localhost` — which, inside the Dev Container,
> isn't the `db` container. `local-docker` runs the app as containers on the
> shared Docker, so it "just works". (Backend code changes need an image rebuild:
> `down` then `local-docker`, or `docker compose --profile app up -d --build backend`.)

---

## One-time setup (so `test` / `test-secrets` work inside)

Two one-time steps enable the staging commands inside the container:

1. **On the host**, dump your keychain secrets into a git-ignored file (repeat
   after rotating a secret). `test-secrets` inside the container reads this,
   since the container has no OS keychain:
   ```bash
   ./scripts/BuildAndDeploy.bash export-secrets   # → .devcontainer/staging.secrets.env (0600, git-ignored)
   ```
2. **In VS Code**, Command Palette → **"Dev Containers: Rebuild Container"**. This
   bakes in `flyctl` + buildx and mounts your `~/.fly` auth (and `~/.claude`).
   Verify after: `flyctl auth whoami` should show your account.

⚠️ `staging.secrets.env` is **plaintext** staging secrets. It's git-ignored
(`*.secrets.env`) — keep it local, never commit it. On the host, `test-secrets`
still prefers the OS keychain.

---

## What runs where

| Command / task | Inside the Dev Container |
|---|---|
| `local-docker`, `down`, `infra`, `status` | ✅ acts on the host's Docker |
| `cd backend && ./gradlew test` | ✅ Testcontainers via the mounted Docker socket |
| `test` (staging deploy) | ✅ `flyctl` + mounted `~/.fly` auth (after Rebuild) |
| `test-secrets` | ✅ reads `.devcontainer/staging.secrets.env` (after `export-secrets`) |
| `local` (host bootRun) | ⚠️ use `local-docker` instead — inside, `bootRun` can't reach `db` at `localhost` |
| `export-secrets` | ✋ **host-only** — it reads the OS keychain |

---

## Payments

`local-docker` **mocks Stripe by default** (`APP_PAYMENTS_PROVIDER=mock`) — no
keys, no network. Register → **Continue to payment** → success page → open
**Mailpit** (<http://localhost:8025>) → click the sign-in link → you're a paid
member. See `payment/MockPaymentProvider`; full details + real-Stripe steps in
`docs/RUNNING-on-host.md` and `docs/STRIPE-TEST-RUNBOOK.md`.

---

## Entering the app containers

`local-docker` runs the app as containers on the host's Docker; exec into them
from the Dev Container terminal (same Docker):

| Container | Enter it |
|---|---|
| Backend | `docker exec -it pollsystem-backend bash` (runtime image: `app.jar` + logs, no source) |
| Frontend | `docker exec -it pollsystem-frontend bash` (Vite dev server, Node 22) |
| Postgres | `docker exec -it pollsystem-db psql -U polladmin -d pollsystem` |
| Mailpit | UI at <http://localhost:8025> |

Logs: `docker compose --profile app logs -f backend` (or `frontend`/`db`/`mailpit`).

---

## Querying the DB

Creds are in `docker-compose.yml` (`polladmin` / `pollpass123`, db `pollsystem`).
The `psql` client is installed in the Dev Container:

```bash
# via the db container (works anywhere):
docker exec -it pollsystem-db psql -U polladmin -d pollsystem -c "SELECT count(*) FROM users;"

# or directly — from inside the Dev Container the db is at host.docker.internal:
PGPASSWORD=pollpass123 psql -h host.docker.internal -U polladmin -d pollsystem
```

---

## Notes

- **Docker socket:** the container uses the host's Docker Desktop socket
  (`~/.docker/desktop/docker.sock`), opened for the `vscode` user by a
  `postStart` chmod. buildx is installed so the backend's `RUN --mount=type=cache`
  (BuildKit) build works.
- **Same paths:** the repo is mounted at the *same absolute path* as on the host,
  so `docker compose` bind-mounts and Testcontainers resolve identically whether
  you run them here or on the host.
- More detail on the container itself: `.devcontainer/README.md`.
