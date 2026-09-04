#!/usr/bin/env bash
#
# BuildAndDeploy.bash — bring the full pollsystem stack up locally.
#
# Orchestrates the "Deploying Locally" recipe (docs/DEPLOYING-LOCAL.md):
#   1. Postgres    — docker container `pollsystem-db`  (docker-compose service `db`)
#   2. Mailpit     — docker container `mailpit`        (captures magic-link email)
#   3. Backend     — Spring Boot via `./gradlew bootRun`, `local` profile, :8080
#   4. Frontend    — Vite dev server via `npm run dev`,                   :3000
#
# Containers are started only if not already running (idempotent). Backend and
# frontend run concurrently; Ctrl-C stops them cleanly and leaves the Docker
# containers up (tear those down with `./scripts/BuildAndDeploy.bash down`).
#
# Usage:
#   ./scripts/BuildAndDeploy.bash [up]     # default: ensure containers, run app
#   ./scripts/BuildAndDeploy.bash down     # stop & remove db + mailpit containers
#   ./scripts/BuildAndDeploy.bash status   # show container status and exit
#   ./scripts/BuildAndDeploy.bash infra    # ensure containers only (no app)
#
# Env overrides:
#   JWT_SECRET   signing key (generated per-run if unset)
#   SKIP_FRONT=1 run backend only
#   SKIP_BACK=1  run frontend only

set -euo pipefail

# --- locate the repo root from this script's location (portable, no hardcoded path)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

DB_CONTAINER="pollsystem-db"
MAILPIT_CONTAINER="mailpit"

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

# --- dispatch ----------------------------------------------------------------
case "${1:-up}" in
  up)     cmd_up ;;
  infra)  ensure_infra ;;
  down)   cmd_down ;;
  status) cmd_status ;;
  *)      die "Unknown command '$1'. Use: up | infra | down | status" ;;
esac
