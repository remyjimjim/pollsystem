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
#   ./scripts/BuildAndDeploy.bash [local]  # default: run the full local stack
#   ./scripts/BuildAndDeploy.bash test     # build + deploy to the staging env
#   ./scripts/BuildAndDeploy.bash test-secrets  # (re)import staging secrets to Fly
#   ./scripts/BuildAndDeploy.bash infra    # local containers only (no app)
#   ./scripts/BuildAndDeploy.bash status   # local container status, then exit
#   ./scripts/BuildAndDeploy.bash down     # stop & remove local db + mailpit
#
# Env overrides (local): JWT_SECRET (generated per-run if unset),
#   SKIP_FRONT=1 (backend only), SKIP_BACK=1 (frontend only).

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
  check_prereqs
  info "Stopping containers (Postgres + Mailpit; keeps the data volume)…"
  docker compose down || true
  ok "Containers stopped. (Add '-v' manually to wipe the DB volume: docker compose down -v)"
}

cmd_status() {
  check_prereqs
  info "Container status:"
  docker ps -a --filter "name=$DB_CONTAINER" --filter "name=$MAILPIT_CONTAINER" \
    --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
}

# --- application: backend + frontend -----------------------------------------
BACK_PID=""; FRONT_PID=""

cleanup() {
  trap - INT TERM EXIT
  echo
  info "Shutting down app processes (containers left running)…"
  # Kill each child's whole process group (setsid made each a group leader),
  # so gradle's spawned JVM and vite's node children go down too.
  [[ -n "$BACK_PID"  ]] && kill -TERM -"$BACK_PID"  2>/dev/null || true
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
  info "Starting backend (Spring Boot, profile=local) on :8080…"
  setsid bash -c 'cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun' &
  BACK_PID=$!
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
  ensure_infra

  trap cleanup INT TERM EXIT
  [[ "${SKIP_BACK:-0}"  == "1" ]] || run_backend
  [[ "${SKIP_FRONT:-0}" == "1" ]] || run_frontend

  echo
  ok "Stack coming up:"
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
  need secret-tool "Install libsecret (Debian/Ubuntu: apt-get install libsecret-tools)."
  # Assemble the KEY=VALUE payload in memory FIRST (dies on any missing key,
  # before Fly is touched), then pipe it in — so a partial set can't happen and
  # no value is ever echoed. Doing the lookups inside the pipe would swallow the
  # die() in a subshell (pipefail) and could import a truncated set.
  local payload="" k v
  for k in "${STAGING_SECRET_KEYS[@]}"; do
    v="$(secret-tool lookup service "$STAGING_KEYCHAIN_SERVICE" account "$k" 2>/dev/null)" \
      || die "Missing keychain secret: service=$STAGING_KEYCHAIN_SERVICE account=$k
  Store it with:  secret-tool store --label='staging $k' service $STAGING_KEYCHAIN_SERVICE account $k"
    payload+="$k=$v"$'\n'
  done
  # Non-secret env, injected the same way (Fly relaxed binding).
  payload+="MAIL_FROM=login@contact.surveysays.buzz"$'\n'
  payload+="APP_BASE_URL=$STAGING_FRONTEND_URL"$'\n'
  info "Importing staging secrets from keychain → $STAGING_FLY_APP (values not printed)…"
  printf '%s' "$payload" | flyctl secrets import -a "$STAGING_FLY_APP"
  ok "Staging secrets imported. The backend redeploys automatically on secret change."
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
  local|up)     cmd_up ;;
  test)         cmd_test ;;
  test-secrets) cmd_test_secrets ;;
  infra)        ensure_infra ;;
  down)         cmd_down ;;
  status)       cmd_status ;;
  *)            die "Unknown command '$1'. Use: local | test | test-secrets | infra | down | status" ;;
esac
