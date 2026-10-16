#!/usr/bin/env bash
# pollsystem.watchall.bash — watch the whole local-docker app in one window.
#
# Follows the logs of the app's containers (pollsystem-backend,
# pollsystem-frontend, pollsystem-db, mailpit), each line prefixed with a
# coloured service label. Error lines (exceptions, ERROR/FATAL, failed
# requests, Vite errors) are highlighted in red and counted; Ctrl-C prints a
# per-service error summary.
#
# It only reads logs: it starts and stops nothing, so it's safe to run any
# time, alongside `./scripts/BuildAndDeploy.bash local-docker`. A container
# that's rebuilt or restarted is picked up again automatically.
#
# Runs from the host or the Dev Container (anywhere the docker CLI works).
#
# (Before 2026-10-16 this script ran the backend and frontend directly on the
# host. That's `./scripts/BuildAndDeploy.bash local` now.)
#
# Usage:
#   scripts/pollsystem.watchall.bash [options] [service…]
#
#   service       backend | frontend | db | mail   (default: all four)
#   -e, --errors  show only error lines (plus watcher notices)
#   -s, --since   how far back to start, e.g. 10m, 1h  (default: 0s = from now)
#   -h, --help    this help
#
# Examples:
#   scripts/pollsystem.watchall.bash                 # everything, from now
#   scripts/pollsystem.watchall.bash -e              # just the errors
#   scripts/pollsystem.watchall.bash -s 30m backend  # backend, last 30 minutes on

set -uo pipefail

# ── options ───────────────────────────────────────────────────────────────
ERRORS_ONLY=0
SINCE="0s"
SERVICES=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        -e|--errors) ERRORS_ONLY=1 ;;
        -s|--since)  SINCE="${2:?--since needs a value, e.g. 10m}"; shift ;;
        -h|--help)   sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        backend|frontend|db|mail) SERVICES+=("$1") ;;
        *) echo "Unknown option or service: $1 (try --help)" >&2; exit 2 ;;
    esac
    shift
done
[[ ${#SERVICES[@]} -gt 0 ]] || SERVICES=(backend frontend db mail)

container_of() {
    case "$1" in
        backend)  echo pollsystem-backend ;;
        frontend) echo pollsystem-frontend ;;
        db)       echo pollsystem-db ;;
        mail)     echo mailpit ;;
    esac
}

# ── colours ───────────────────────────────────────────────────────────────
# Only on a terminal, so piped output (e.g. into tee) stays plain.
if [[ -t 1 ]] && command -v tput >/dev/null 2>&1; then
    C_RESET="$(tput sgr0)"; C_BOLD="$(tput bold)"
    C_ERR="$(tput setaf 1)"; C_INFO="$(tput setaf 3)"
    declare -A C_SVC=(
        [backend]="$(tput setaf 2)" [frontend]="$(tput setaf 6)"
        [db]="$(tput setaf 4)"      [mail]="$(tput setaf 5)"
    )
else
    C_RESET= C_BOLD= C_ERR= C_INFO=
    declare -A C_SVC=([backend]= [frontend]= [db]= [mail]=)
fi

info() { printf '%s[watchall]%s %s\n' "$C_INFO" "$C_RESET" "$*"; }

# What counts as an error, per service (extended regex, case-sensitive on
# purpose: "error" in normal text like "errorHandler" shouldn't count).
error_pattern() {
    case "$1" in
        backend)  echo ' ERROR |Exception|APPLICATION FAILED|Caused by:|FATAL' ;;
        frontend) echo 'Internal server error|\[vite\].*[Ee]rror|Error:|ERR!|Failed to' ;;
        db)       echo 'ERROR:|FATAL:|PANIC:' ;;
        mail)     echo '\[error\]|level=error|ERROR' ;;
    esac
}

command -v docker >/dev/null 2>&1 || { echo "docker isn't on PATH" >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker isn't reachable (is the engine running? see scripts/docker-doctor.bash)" >&2; exit 1; }

# Error counts go to one small file per service (the followers are subshells).
COUNTS="$(mktemp -d)"

# ── one follower per service ──────────────────────────────────────────────
# Follows the container's logs; when it stops (rebuild, restart), waits for it
# to come back and resumes from that moment, so nothing is shown twice.
follow() {
    local svc="$1" name; name="$(container_of "$svc")"
    local label; label="$(printf '%s[%-8s]%s' "${C_SVC[$svc]}" "$svc" "$C_RESET")"
    local pattern; pattern="$(error_pattern "$svc")"
    local since="$SINCE" waiting=0
    : > "$COUNTS/$svc"
    while true; do
        if ! docker ps --format '{{.Names}}' | grep -qx "$name"; then
            [[ $waiting -eq 1 ]] || info "$name isn't running; waiting for it…"
            waiting=1; sleep 3; continue
        fi
        [[ $waiting -eq 0 ]] || info "$name is up; following its logs"
        waiting=0
        docker logs -f --since "$since" "$name" 2>&1 | while IFS= read -r line; do
            if [[ "$line" =~ $pattern ]]; then
                echo x >> "$COUNTS/$svc"
                printf '%s %s%s%s%s\n' "$label" "$C_BOLD" "$C_ERR" "$line" "$C_RESET"
            elif [[ $ERRORS_ONLY -eq 0 ]]; then
                printf '%s %s\n' "$label" "$line"
            fi
        done
        # The stream ended: the container stopped. Resume from now when it's back.
        since="$(date -u +%Y-%m-%dT%H:%M:%S)"
        info "$name stopped (rebuild or restart?)"
    done
}

summary() {
    trap - EXIT INT
    # Stop every follower and its `docker logs` child: they share this
    # script's process group. Ignore TERM ourselves so we can print the summary.
    trap '' TERM
    kill -TERM 0 2>/dev/null
    wait 2>/dev/null
    echo
    info "errors seen while watching:"
    local total=0 n
    for svc in "${SERVICES[@]}"; do
        n=$(wc -l < "$COUNTS/$svc" 2>/dev/null || echo 0)
        total=$((total + n))
        if [[ $n -gt 0 ]]; then
            printf '  %s%-8s %s%d%s\n' "${C_SVC[$svc]}" "$svc" "$C_ERR" "$n" "$C_RESET"
        else
            printf '  %s%-8s%s 0\n' "${C_SVC[$svc]}" "$svc" "$C_RESET"
        fi
    done
    [[ $total -eq 0 ]] && info "no errors." || info "$total error line(s); scroll up for the red ones."
    rm -rf "$COUNTS"
    exit 0
}
trap summary EXIT INT TERM

info "watching: ${SERVICES[*]}  (since ${SINCE}; $([[ $ERRORS_ONLY -eq 1 ]] && echo 'errors only' || echo 'all output'))"
info "Ctrl-C to stop and see the error summary"
for svc in "${SERVICES[@]}"; do follow "$svc" & done
wait
