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

## Seeding a demo database

```bash
./scripts/demo/seed.bash
```

Starts the local database over with a demo: per state (Washington, Colorado,
California) 10 respondents, a member, a creator and an admin, all
`demo-…@test.local`. Each creator has a draft election (4 candidates, 3 ballot
measures) and 3 draft questionnaires; each admin has 3 draft questionnaires
and admin access for the state. Access comes from real requests, approved by
`demo-super@test.local`.

It first deletes the previous demo and the e2e `zzz` users; everyone else is
left alone. Re-run it any time. It needs the local dev endpoints, so it works
only against the local stack. From the Dev Container, where `localhost:8080`
isn't reachable, run it in a container on the host network:
`docker run --rm --network host -v "$PWD":/repo -w /repo alpine:3.20 sh -c "apk add -q bash curl python3 && bash scripts/demo/seed.bash"`.

## Dev settings (local only)

http://localhost:3000/dev/settings (also linked from the header, and from the
Login page's Quick sign-in panel) sets the **results privacy threshold**:
results stay hidden until a poll has that many respondents, then update in
steps of it, and smaller groups are withheld. Prod uses the default, 4 (`app.results.k-anonymity-threshold`).

- **Save** rebuilds every poll's results for the new value at once, with no
  emails sent. **Back to 4** (the default) clears it.
- It's kept in the local database, so it survives rebuilds.
- Only development builds have the page, and only the `local` backend profile
  honours the setting: staging and prod ignore it.
- The e2e specs assume the default. A run notes your value, uses the default,
  and puts yours back at the end (`frontend/playwright/global-setup.ts` and
  `global-teardown.ts`).

## Watching the whole app for errors

```bash
./scripts/pollsystem.watchall.bash            # all four containers, from now
./scripts/pollsystem.watchall.bash -e         # only error lines
./scripts/pollsystem.watchall.bash -s 30m db  # one service, from 30 minutes ago
```

It follows the logs of `pollsystem-backend`, `pollsystem-frontend`,
`pollsystem-db` and `mailpit` in one window, each line labelled by service.
Error lines are shown in red and counted, and **Ctrl-C** prints how many each
service logged.

It only reads logs, so run it any time next to the running app. A container
that's rebuilt or restarted is picked up again. It works from the host or the
Dev Container.

(It used to run the backend and frontend on the host; that's
`./scripts/BuildAndDeploy.bash local` now, see RUNNING-on-host.md.)

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

## Resetting the DB

Postgres data lives in the **named volume `pollsystem-data`**, so it **survives
`down`** (which only removes containers). To actually clear data:

- **Wipe everything** (drop the volume → fresh, empty DB):
  ```bash
  docker compose down -v                        # removes containers + the pollsystem-data volume
  ./scripts/BuildAndDeploy.bash local-docker    # bring it back up, empty
  ```
- **Clear just the test users** (keep the schema + volume): the `local` profile
  exposes a dev cleanup endpoint —
  ```bash
  curl -X POST 'http://localhost:8080/api/dev/reset-test-users?emailPrefix=zzz'
  ```
  Deletes users whose email starts with `emailPrefix` (plus their poll data);
  it refuses a prefix shorter than 3 characters as a safety guard. The Playwright
  e2e specs call this in `beforeAll`.

> These run from inside the Dev Container **or** the host — same Docker daemon via
> the mounted socket. A plain `down` keeps the data; only `-v` (or
> `docker volume rm pollsystem_pollsystem-data`) wipes it — and the volume can't
> be removed while the `pollsystem-db` container still exists.

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
