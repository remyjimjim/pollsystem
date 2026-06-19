#!/usr/bin/env bash
# pollsystem.watchall.bash — orchestrate the three-terminal dev loop in one
# place. Streams output from each service prefixed with a coloured label,
# and tears all of them down cleanly on Ctrl-C.
#
#   1. gradle -t compileKotlin   (continuous Kotlin compile → DevTools restart)
#   2. gradle bootRun            (backend on :8080, local profile)
#   3. npm run dev               (frontend / Vite on :3000)
#
# Docker-hosted dependencies (pollsystem-db + mailpit) must already be up;
# the script checks and bails with instructions if not. See docs/RUNNING.md.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# ── colours ───────────────────────────────────────────────────────────────
# Use tput if a TTY is attached; otherwise no escapes so logs stay readable
# when this script is piped (e.g. into tee).
if [[ -t 1 ]] && command -v tput >/dev/null 2>&1; then
    C_RESET="$(tput sgr0)"
    C_WATCH="$(tput setaf 4)"   # blue
    C_BACK="$(tput setaf 2)"    # green
    C_FRONT="$(tput setaf 6)"   # cyan
    C_INFO="$(tput setaf 3)"    # yellow
    C_ERR="$(tput setaf 1)"     # red
else
    C_RESET= C_WATCH= C_BACK= C_FRONT= C_INFO= C_ERR=
fi

info()  { printf '%s[watchall]%s %s\n' "$C_INFO" "$C_RESET" "$*"; }
fatal() { printf '%s[watchall]%s %s\n' "$C_ERR"  "$C_RESET" "$*" >&2; exit 1; }

# ── preflight ─────────────────────────────────────────────────────────────
command -v docker  >/dev/null 2>&1 || fatal "docker not on PATH"
command -v npm     >/dev/null 2>&1 || fatal "npm not on PATH"
[[ -x "$ROOT/backend/gradlew" ]]   || fatal "backend/gradlew not found at $ROOT/backend/gradlew"
[[ -d "$ROOT/frontend/node_modules" ]] || info "frontend/node_modules missing — Vite will fail until 'npm install' runs"

require_container() {
    local name="$1"
    if ! docker ps --format '{{.Names}}' | grep -qx "$name"; then
        fatal "container '$name' is not running. Start docker and the pollsystem-db + mailpit containers (see docs/RUNNING.md), then re-run."
    fi
}
require_container pollsystem-db
require_container mailpit

# ── child process bookkeeping ─────────────────────────────────────────────
pids=()
cleanup() {
    info "stopping all services…"
    # Kill the whole process group of each child so daemonised grandchildren
    # (Vite, gradle workers) go down too.
    for pid in "${pids[@]:-}"; do
        if kill -0 "$pid" 2>/dev/null; then
            kill -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
        fi
    done
    wait 2>/dev/null || true
    info "done."
}
trap cleanup EXIT INT TERM

# ── launcher ──────────────────────────────────────────────────────────────
# launch <label> <colour> <cwd> <cmd…>
# Prefixes every line of the child's combined stdout/stderr with [label],
# and tracks its PID so cleanup() can kill the whole tree.
launch() {
    local label="$1" colour="$2" cwd="$3"; shift 3
    local prefix="${colour}[${label}]${C_RESET}"

    # setsid + bash -c puts the child in its own process group so we can
    # signal the whole subtree on cleanup.
    setsid bash -c "cd '$cwd' && $* 2>&1" | while IFS= read -r line; do
        printf '%s %s\n' "$prefix" "$line"
    done &
    pids+=("$!")
}

info "project root: $ROOT"
info "launching three services — Ctrl-C to stop them all"

launch "watch" "$C_WATCH" "$ROOT/backend"  './gradlew -t compileKotlin --console=plain'
sleep 1   # give the watcher a head start so its log doesn't interleave the bootRun banner
launch "back"  "$C_BACK"  "$ROOT/backend"  'SPRING_PROFILES_ACTIVE=local ./gradlew bootRun --console=plain'
launch "front" "$C_FRONT" "$ROOT/frontend" 'npm run dev'

# Wait for any child to exit, then trigger cleanup of the rest.
wait -n 2>/dev/null || true
info "a service exited — tearing down the others"
