#!/usr/bin/env bash
#
# BuildAndDeploy.bash — run pollsystem locally, or deploy it to the test env.
#
# LOCAL (docs/DEPLOYING-LOCAL.md) — the default. Brings up the full stack:
#   1. Postgres    — docker container `pollsystem-db`  (docker-compose service `db`)
#   2. Mailpit     — docker container `mailpit`        (captures magic-link email)
#   3. Backend     — Spring Boot via `./gradlew bootRun`, `local` profile, :8080
#   4. Frontend    — Vite dev server via `npm run dev`,                   :3000
#   Containers are started only if not already running (idempotent). Backend and
#   frontend run concurrently; Ctrl-C stops them and leaves the containers up.
#
# TEST (docs/ENVIRONMENTS.md, DEPLOYING-FLY.md, DEPLOYING-CLOUDFLARE-PAGES.md) —
#   deploys to the staging environment:
#     - Backend  → Fly app `pollsystem-backend-staging` (Docker build, Neon branch DB)
#     - Frontend → pushes the `staging` git branch; Cloudflare Pages auto-builds it
#       and serves https://staging.pollsystem.pages.dev
#   Secrets are set once via `test-secrets` (imported from the OS keychain).
#
# Usage:
#   ./scripts/BuildAndDeploy.bash [local]  # default: full local stack (gradle + vite on host)
#   ./scripts/BuildAndDeploy.bash local-docker  # full stack, everything in Docker (compose `app` profile)
#   ./scripts/BuildAndDeploy.bash test     # build + deploy to the staging env
#   ./scripts/BuildAndDeploy.bash test-secrets  # (re)import staging secrets to Fly (keychain, or the fallback file inside the Dev Container)
#   ./scripts/BuildAndDeploy.bash export-secrets # (host) dump keychain secrets to a git-ignored file for use inside the Dev Container
#   ./scripts/BuildAndDeploy.bash infra    # local containers only (no app)
#   ./scripts/BuildAndDeploy.bash status   # local container status, then exit
#   ./scripts/BuildAndDeploy.bash down     # stop & remove local db + mailpit
#
# Env overrides (local): JWT_SECRET (generated per-run if unset),
#   SKIP_FRONT=1 (backend only), SKIP_BACK=1 (frontend only),
#   SKIP_WATCH=1 (no Kotlin watcher — disables backend hot reload),
#   APP_PAYMENTS_PROVIDER=stripe (use real Stripe test mode; default is `mock`).

set -euo pipefail

# --- locate the repo root from this script's location (portable, no hardcoded path)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

DB_CONTAINER="pollsystem-db"
MAILPIT_CONTAINER="mailpit"

# --- staging / test environment ---------------------------------------------
STAGING_FLY_APP="pollsystem-backend-staging"
STAGING_FLY_CONFIG="fly.staging.toml"          # relative to backend/
STAGING_BRANCH="staging"
STAGING_KEYCHAIN_SERVICE="pollsystem-fly-staging"
STAGING_BACKEND_URL="https://pollsystem-backend-staging.fly.dev"
STAGING_FRONTEND_URL="https://staging.pollsystem.pages.dev"
# Secrets pulled from the OS keychain (service=$STAGING_KEYCHAIN_SERVICE) and
# imported into Fly by `test-secrets`. Non-secret env (MAIL_FROM, APP_BASE_URL)
# is appended inline there.
STAGING_SECRET_KEYS=(
  SPRING_DATASOURCE_URL
  SPRING_DATASOURCE_USERNAME
  SPRING_DATASOURCE_PASSWORD
  JWT_SECRET
  RESEND_API_KEY
)
# Fallback secrets source for environments without the OS keychain (e.g. the Dev
# Container): a git-ignored KEY=VALUE file, generated on the host from the
# keychain via `export-secrets`. Plaintext — keep it local, never commit it.
STAGING_SECRETS_FILE="$ROOT/.devcontainer/staging.secrets.env"

# --- pretty logging ----------------------------------------------------------
if [[ -t 1 ]]; then
  C_BLUE=$'\033[34m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_RED=$'\033[31m'; C_OFF=$'\033[0m'
else
  C_BLUE=''; C_GREEN=''; C_YELLOW=''; C_RED=''; C_OFF=''
fi
info()  { printf '%s==>%s %s\n' "$C_BLUE"  "$C_OFF" "$*"; }
ok()    { printf '%s ✓ %s%s\n'  "$C_GREEN" "$*" "$C_OFF"; }
warn()  { printf '%s ! %s%s\n'  "$C_YELLOW" "$*" "$C_OFF"; }
die()   { printf '%s ✗ %s%s\n'  "$C_RED"   "$*" "$C_OFF" >&2; exit 1; }

# --- prerequisite checks -----------------------------------------------------
need() { command -v "$1" >/dev/null 2>&1 || die "'$1' not found on PATH. $2"; }

check_prereqs() {
  need docker "Install Docker: https://docs.docker.com/get-docker/"
  docker info >/dev/null 2>&1 || die "Docker daemon is not running. Start Docker and retry."
  docker compose version >/dev/null 2>&1 || die "'docker compose' plugin not available."
}

# --- generic wait-for helper -------------------------------------------------
# wait_for <description> <max_seconds> <command...>
wait_for() {
  local desc="$1" max="$2"; shift 2
  local i=0
  until "$@" >/dev/null 2>&1; do
    i=$((i + 1))
    if (( i > max )); then die "Timed out after ${max}s waiting for ${desc}."; fi
    sleep 1
  done
  ok "$desc ready"
}

container_running() { [[ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" == "true" ]]; }

# Block until the first of our app children exits. Portable substitute for
# bash 4.3+ `wait -n` (absent on e.g. macOS's stock bash 3.2): poll the PIDs
# with `kill -0`. Returns as soon as one has gone, so cleanup can down the rest.
wait_any() {
  local pids=("$@") pid alive
  # Nothing to wait on (both children skipped) — return immediately.
  [[ ${#pids[@]} -gt 0 ]] || return 0
  while :; do
    alive=0
    for pid in "${pids[@]}"; do
      [[ -n "$pid" ]] || continue
      if kill -0 "$pid" 2>/dev/null; then
        alive=$((alive + 1))
      else
        return 0   # this child has exited
      fi
    done
    (( alive > 0 )) || return 0   # all tracked children gone
    sleep 1
  done
}

# --- infrastructure: Postgres + Mailpit --------------------------------------
ensure_postgres() {
  # `docker compose up -d db` is itself idempotent: no-op if running, starts it
  # if stopped or absent. We branch only to log intent clearly.
  if container_running "$DB_CONTAINER"; then
    ok "Postgres ($DB_CONTAINER) already running"
  else
    info "Starting Postgres ($DB_CONTAINER)…"
    docker compose up -d db
  fi
  # Wait until Postgres actually accepts connections — avoids the classic
  # "Connection refused" backend boot race (see DEPLOYING-LOCAL.md common issues).
  wait_for "Postgres" 60 docker exec "$DB_CONTAINER" pg_isready -U polladmin -d pollsystem
}

ensure_mailpit() {
  # Mailpit is a docker-compose service. `up -d` is idempotent: no-op if
  # running, starts it if stopped or absent. We branch only to log intent.
  if container_running "$MAILPIT_CONTAINER"; then
    ok "Mailpit ($MAILPIT_CONTAINER) already running"
  else
    info "Starting Mailpit ($MAILPIT_CONTAINER)…"
    docker compose up -d mailpit
  fi
  ok "Mailpit SMTP :1025 · web UI http://localhost:8025"
}

ensure_infra() {
  check_prereqs
  ensure_postgres
  ensure_mailpit
}

# --- teardown / status subcommands -------------------------------------------
cmd_down() {
  # Reap host dev processes from a `local` run first — this needs no Docker, and
  # `down` used to leave orphaned bootRun/Vite/watcher squatting :8080/:3000.
  info "Reaping any lingering dev processes (bootRun / Vite / watcher)…"
  free_port 8080
  free_port 3000
  reap_watcher

  check_prereqs
  info "Stopping containers (Postgres + Mailpit; keeps the data volume)…"
  docker compose down || true
  ok "Stopped. (Add '-v' manually to wipe the DB volume: docker compose down -v)"
}

cmd_status() {
  check_prereqs
  info "Container status:"
  docker ps -a --filter "name=$DB_CONTAINER" --filter "name=$MAILPIT_CONTAINER" \
    --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
}

# --- reap orphaned host dev processes ----------------------------------------
# A `local` run's backend (bootRun, :8080), Kotlin watcher, and frontend (Vite,
# :3000) run as HOST processes. If a run dies ungracefully (hard kill, closed
# terminal) the Ctrl-C cleanup never fires, and a lingering Gradle daemon can
# keep the JVM alive — orphaning it. `down` only stops containers, so a stale
# JVM then squats :8080 and blocks the next `local`. These reap that.

# Terminate whatever host process is LISTENING on a TCP port (TERM, then KILL).
free_port() {
  local port="$1" pids
  pids=$(ss -ltnpH "sport = :$port" 2>/dev/null | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -u)
  [[ -z "$pids" ]] && return 0
  warn "freeing lingering process on :$port (pid $(echo "$pids" | tr '\n' ' '))"
  kill $pids 2>/dev/null || true
  sleep 1
  pids=$(ss -ltnpH "sport = :$port" 2>/dev/null | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -u)
  [[ -n "$pids" ]] && kill -9 $pids 2>/dev/null || true
  return 0
}

# Reap a lingering continuous-compile watcher (it holds no port). Runs from the
# script file, so the pattern can't match this process's own argv.
reap_watcher() {
  local pids
  pids=$(pgrep -f 'gradlew -t classes' 2>/dev/null || true)
  [[ -z "$pids" ]] && return 0
  warn "reaping lingering Kotlin watcher (pid $(echo "$pids" | tr '\n' ' '))"
  kill $pids 2>/dev/null || true
  return 0
}

# --- application: backend + frontend -----------------------------------------
BACK_PID=""; FRONT_PID=""; WATCH_PID=""

cleanup() {
  trap - INT TERM EXIT
  echo
  info "Shutting down app processes (containers left running)…"
  # Kill each child's whole process group (setsid made each a group leader),
  # so gradle's spawned JVM and vite's node children go down too.
  [[ -n "$BACK_PID"  ]] && kill -TERM -"$BACK_PID"  2>/dev/null || true
  [[ -n "$WATCH_PID" ]] && kill -TERM -"$WATCH_PID" 2>/dev/null || true
  [[ -n "$FRONT_PID" ]] && kill -TERM -"$FRONT_PID" 2>/dev/null || true
  wait 2>/dev/null || true
  ok "Stopped. Containers still up — './scripts/BuildAndDeploy.bash down' to remove them."
}

run_backend() {
  # The `local` profile is mandatory: it wires mail to Mailpit and sets the
  # magic-link base URL to the Vite dev server. Without it, sign-in email
  # silently fails (DEPLOYING-LOCAL.md §3).
  : "${JWT_SECRET:=$(openssl rand -hex 32 2>/dev/null || echo dev-only-insecure-secret-change-me)}"
  export JWT_SECRET
  # Default local dev to the offline mock payment provider (no Stripe keys or
  # webhooks needed; see payment/MockPaymentProvider). To test real Stripe test
  # mode instead: APP_PAYMENTS_PROVIDER=stripe + STRIPE_* + `stripe listen`.
  : "${APP_PAYMENTS_PROVIDER:=mock}"
  export APP_PAYMENTS_PROVIDER
  info "Starting backend (Spring Boot, profile=local, payments=$APP_PAYMENTS_PROVIDER) on :8080…"
  setsid bash -c 'cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun' &
  BACK_PID=$!
}

# Continuous Kotlin compilation for backend hot reload. On save it recompiles
# into build/classes, which Spring DevTools (a bootRun-only dependency) watches
# — it then restarts the app context in ~2-3s, no manual restart. Runs as a
# second gradle build alongside bootRun (verified fine on Gradle 8.10). Opt out
# with SKIP_WATCH=1 (e.g. if your IDE already compiles to build/classes).
run_backend_watch() {
  info "Starting Kotlin watcher (continuous compile → DevTools hot reload)…"
  setsid bash -c 'cd backend && ./gradlew -t classes' &
  WATCH_PID=$!
}

run_frontend() {
  if [[ ! -d frontend/node_modules ]]; then
    info "Installing frontend dependencies (first run)…"
    (cd frontend && npm install)
  fi
  info "Starting frontend (Vite dev server) on :3000…"
  setsid bash -c 'cd frontend && npm run dev' &
  FRONT_PID=$!
}

cmd_up() {
  # Preflight: reap orphaned dev processes from a previous run so a stale bootRun
  # JVM (or Vite) squatting :8080/:3000 can't block this start.
  [[ "${SKIP_BACK:-0}"  == "1" ]] || free_port 8080
  [[ "${SKIP_FRONT:-0}" == "1" ]] || free_port 3000
  [[ "${SKIP_BACK:-0}" == "1" || "${SKIP_WATCH:-0}" == "1" ]] || reap_watcher

  ensure_infra

  trap cleanup INT TERM EXIT
  [[ "${SKIP_BACK:-0}"  == "1" ]] || run_backend
  [[ "${SKIP_BACK:-0}"  == "1" || "${SKIP_WATCH:-0}" == "1" ]] || run_backend_watch
  [[ "${SKIP_FRONT:-0}" == "1" ]] || run_frontend

  echo
  ok "Stack coming up (backend + frontend hot-reload on save):"
  echo "    Frontend   http://localhost:3000"
  echo "    Backend    http://localhost:8080/api"
  echo "    Mailpit    http://localhost:8025   (magic-link emails land here)"
  echo "    Postgres   localhost:5432          (polladmin / pollpass123)"
  echo
  info "Press Ctrl-C to stop the app (containers stay up)."

  # Block until either child exits; if one dies, the EXIT trap (cleanup) takes
  # the other down. Portable — no dependency on bash 4.3+ `wait -n`.
  wait_any "$BACK_PID" "$FRONT_PID"
}

# --- LOCAL, fully containerized ----------------------------------------------
# Same stack as `local`, but the backend and frontend run in Docker too (compose
# `app` profile) instead of via gradle/vite on the host — one command, no local
# JDK/Node needed. Detached: containers keep running after this returns.
cmd_up_docker() {
  check_prereqs
  info "Building + starting the full containerized stack (db + mailpit + backend + frontend)…"
  docker compose --profile app up -d --build

  # Probe readiness via Docker logs, not a network address. The socket is always
  # reachable, so this works whether the command runs on the host OR inside the
  # Dev Container — where `localhost:8080` isn't the backend (its port publishes
  # to the host) and `host.docker.internal` can resolve to a flaky IPv6 address.
  # Each app logs a clear line when it's up.
  info "Waiting for the backend to finish starting…"
  wait_for "backend startup" 180 bash -c \
    'docker logs pollsystem-backend 2>&1 | grep -q "Started PollSystemApplicationKt"'
  info "Waiting for the frontend dev server…"
  wait_for "frontend dev server" 90 bash -c \
    'docker logs pollsystem-frontend 2>&1 | grep -qiE "ready in|Local:"'

  echo
  ok "Containerized stack up (all services in Docker):"
  echo "    Frontend   http://localhost:3000   (Vite dev server, HMR)"
  echo "    Backend    http://localhost:8080/api"
  echo "    Mailpit    http://localhost:8025   (magic-link emails land here)"
  echo "    Postgres   localhost:5432          (polladmin / pollpass123)"
  echo
  info "Stop everything with:  ./scripts/BuildAndDeploy.bash down"
}

# --- TEST / staging deploy ---------------------------------------------------
require_fly() {
  need flyctl "Install: curl -L https://fly.io/install.sh | sh"
  flyctl auth whoami >/dev/null 2>&1 || die "Not logged into Fly. Run: flyctl auth login"
}

# Import staging secrets into the Fly app from the OS keychain, so no secret
# value is ever typed on a command line or echoed to the terminal/history.
# Store each one first, e.g.:
#   secret-tool store --label='staging JWT_SECRET' \
#     service pollsystem-fly-staging account JWT_SECRET
cmd_test_secrets() {
  require_fly
  # Assemble the KEY=VALUE payload in memory FIRST (dies on any missing key,
  # before Fly is touched), then pipe it in — so a partial set can't happen and
  # no value is ever echoed. Source of the secrets: the OS keychain on the host,
  # or the git-ignored fallback file inside the Dev Container (no keychain there).
  local payload="" k v
  if command -v secret-tool >/dev/null 2>&1; then
    info "Reading staging secrets from the OS keychain…"
    for k in "${STAGING_SECRET_KEYS[@]}"; do
      v="$(secret-tool lookup service "$STAGING_KEYCHAIN_SERVICE" account "$k" 2>/dev/null)" \
        || die "Missing keychain secret: service=$STAGING_KEYCHAIN_SERVICE account=$k
  Store it with:  secret-tool store --label='staging $k' service $STAGING_KEYCHAIN_SERVICE account $k"
      payload+="$k=$v"$'\n'
    done
  elif [[ -f "$STAGING_SECRETS_FILE" ]]; then
    warn "No OS keychain here — reading staging secrets from $STAGING_SECRETS_FILE (plaintext)."
    local -A _env
    # Parse KEY=VALUE without sourcing — values may contain & ? = spaces. The
    # `|| [[ -n "$fk" ]]` catches a final line with no trailing newline.
    while IFS='=' read -r fk fv || [[ -n "$fk" ]]; do
      [[ "$fk" =~ ^[[:space:]]*# || -z "${fk// /}" ]] && continue
      _env["${fk// /}"]="$fv"
    done < "$STAGING_SECRETS_FILE"
    for k in "${STAGING_SECRET_KEYS[@]}"; do
      v="${_env[$k]:-}"
      [[ -n "$v" ]] || die "Missing '$k' in $STAGING_SECRETS_FILE — regenerate on the host: ./scripts/BuildAndDeploy.bash export-secrets"
      payload+="$k=$v"$'\n'
    done
  else
    die "No secret source found. Either run this on the host (with the keychain), or
  generate the fallback file on the host first:  ./scripts/BuildAndDeploy.bash export-secrets
  (writes $STAGING_SECRETS_FILE — git-ignored — for use inside the Dev Container)."
  fi
  # Non-secret env, injected the same way (Fly relaxed binding).
  payload+="MAIL_FROM=login@contact.surveysays.buzz"$'\n'
  payload+="APP_BASE_URL=$STAGING_FRONTEND_URL"$'\n'
  info "Importing staging secrets → $STAGING_FLY_APP (values not printed)…"
  printf '%s' "$payload" | flyctl secrets import -a "$STAGING_FLY_APP"
  ok "Staging secrets imported. The backend redeploys automatically on secret change."
}

# Export staging secrets from the OS keychain into the git-ignored fallback file,
# so `test-secrets` works inside the Dev Container (which has no keychain). Run
# this ONCE on the host (and after rotating a secret). The file is plaintext.
cmd_export_secrets() {
  need secret-tool "Install libsecret (host only): apt-get install libsecret-tools."
  local content="" k v
  for k in "${STAGING_SECRET_KEYS[@]}"; do
    v="$(secret-tool lookup service "$STAGING_KEYCHAIN_SERVICE" account "$k" 2>/dev/null)" \
      || die "Missing keychain secret: service=$STAGING_KEYCHAIN_SERVICE account=$k"
    content+="$k=$v"$'\n'
  done
  mkdir -p "$(dirname "$STAGING_SECRETS_FILE")"
  ( umask 077; printf '%s' "$content" > "$STAGING_SECRETS_FILE" )
  ok "Wrote $STAGING_SECRETS_FILE (0600, git-ignored)."
  warn "It holds PLAINTEXT staging secrets — keep it local, never commit it."
}

cmd_test() {
  require_fly
  [[ -f "backend/$STAGING_FLY_CONFIG" ]] || die "Missing backend/$STAGING_FLY_CONFIG"
  flyctl status -a "$STAGING_FLY_APP" >/dev/null 2>&1 \
    || die "Fly app '$STAGING_FLY_APP' not found. Create it first: flyctl apps create $STAGING_FLY_APP
  Then set secrets: ./scripts/BuildAndDeploy.bash test-secrets"

  # Cloudflare Pages only builds committed + pushed code, so the frontend
  # deploy reflects HEAD, not the working tree. Warn if they differ.
  if [[ -n "$(git status --porcelain)" ]]; then
    warn "Working tree has uncommitted changes — the frontend deploy pushes committed HEAD only."
  fi

  # Frontend pre-check: build locally to fail fast before pushing (Cloudflare
  # rebuilds it too, but this catches type/build errors without a round-trip).
  if [[ ! -d frontend/node_modules ]]; then
    info "Installing frontend dependencies (first run)…"
    (cd frontend && npm ci)
  fi
  info "Building frontend locally (pre-check)…"
  (cd frontend && npm run build)

  # Backend: Fly builds the fat JAR from backend/Dockerfile and deploys it.
  info "Deploying backend → $STAGING_FLY_APP (Fly Docker build)…"
  (cd backend && flyctl deploy -a "$STAGING_FLY_APP" -c "$STAGING_FLY_CONFIG")

  # Frontend: push HEAD to the staging branch; Cloudflare Pages auto-builds it.
  info "Deploying frontend → '$STAGING_BRANCH' branch (Cloudflare Pages auto-build)…"
  git push origin "HEAD:$STAGING_BRANCH"

  echo
  ok "Staging deploy triggered:"
  echo "    Backend    $STAGING_BACKEND_URL/actuator/health"
  echo "    Frontend   $STAGING_FRONTEND_URL   (Pages builds the pushed branch — watch the CF dashboard)"
  echo
  info "Waiting for the staging backend to report healthy…"
  wait_for "staging backend health" 180 curl -sf "$STAGING_BACKEND_URL/actuator/health"
}

# --- dispatch ----------------------------------------------------------------
case "${1:-local}" in
  local|up)           cmd_up ;;
  local-docker|docker) cmd_up_docker ;;
  test)               cmd_test ;;
  test-secrets)       cmd_test_secrets ;;
  export-secrets)     cmd_export_secrets ;;
  infra)              ensure_infra ;;
  down)               cmd_down ;;
  status)             cmd_status ;;
  *)                  die "Unknown command '$1'. Use: local | local-docker | test | test-secrets | export-secrets | infra | down | status" ;;
esac
