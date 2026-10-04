#!/usr/bin/env bash
#
# docker-doctor.bash — diagnose (and optionally fix) the host Docker setup the
# Dev Container depends on.
#
# Background: dev runs on NATIVE docker-ce (system `docker.service`, socket
# /var/run/docker.sock). Docker Desktop is retired (its qemu VM kept crashing and
# eats 8 GB of RAM), but it keeps resurrecting itself — via its app launcher or
# anything that runs `systemctl --user start docker-desktop` — and when it does:
#   1. it flips the CLI context to `desktop-linux` → `docker` talks to Desktop's
#      dead socket (`500 Internal Server Error`), so the Dev Container won't attach;
#   2. its port forwarder squats on the stack's host ports (3000 8080 8025 5432
#      1025) → `docker compose up` fails with "address already in use";
#   3. its VM pins 8 GB, pushing the laptop into swap.
# Restarting Docker doesn't help because the wrong Docker is the one running.
#
# Usage (run on the HOST, not inside the Dev Container):
#   ./scripts/docker-doctor.bash          # diagnose only, changes nothing
#   ./scripts/docker-doctor.bash --fix    # stop + mask Docker Desktop, reset the
#                                         # context, (re)start native docker
#
# Undo the mask if you ever want Desktop back:
#   systemctl --user unmask docker-desktop

set -uo pipefail

FIX=false
case "${1:-}" in
  --fix) FIX=true ;;
  ""|--check) ;;
  -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
  *) echo "unknown option: $1 (try --help)" >&2; exit 2 ;;
esac

STACK_PORTS=(3000 8080 8025 5432 1025)
NATIVE_SOCK=/var/run/docker.sock
DESKTOP_SOCK="$HOME/.docker/desktop/docker.sock"
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"

if [[ -t 1 ]]; then R=$'\e[31m'; G=$'\e[32m'; Y=$'\e[33m'; B=$'\e[1m'; N=$'\e[0m'; else R= G= Y= B= N=; fi
PROBLEMS=0
ok()   { echo "  ${G}✔${N} $*"; }
warn() { echo "  ${Y}!${N} $*"; }
bad()  { echo "  ${R}✘${N} $*"; PROBLEMS=$((PROBLEMS + 1)); }
hdr()  { echo; echo "${B}$*${N}"; }

if [[ -f /.dockerenv || -n "${REMOTE_CONTAINERS:-}" ]]; then
  echo "${R}Run this on the host, not inside the Dev Container.${N}" >&2
  exit 2
fi

desktop_running() {
  systemctl --user is-active --quiet docker-desktop 2>/dev/null ||
    pgrep -u "$(id -u)" -f '/opt/docker-desktop/bin/com.docker.backend' >/dev/null
}

diagnose() {
  hdr "Native Docker Engine (docker.service)"
  if systemctl is-active --quiet docker; then ok "docker.service is active"; else bad "docker.service is not active"; fi
  if [[ -S $NATIVE_SOCK ]]; then ok "socket $NATIVE_SOCK exists ($(stat -c '%U:%G %a' $NATIVE_SOCK))"; else bad "socket $NATIVE_SOCK missing"; fi
  if id -nG | tr ' ' '\n' | grep -qx docker; then ok "user $(id -un) is in the docker group"
  else bad "user $(id -un) is not in the docker group (sudo usermod -aG docker $(id -un), then log out/in)"; fi
  if v=$(docker --context default version --format '{{.Server.Version}}' 2>/dev/null) && [[ -n $v ]]; then
    ok "native engine answers (server $v)"
  else bad "native engine does not answer on $NATIVE_SOCK"; fi

  hdr "Docker CLI context"
  local ctx; ctx=$(docker context show 2>/dev/null || echo '?')
  if [[ $ctx == default ]]; then ok "current context: default"; else bad "current context: $ctx (should be 'default')"; fi
  if [[ -n ${DOCKER_HOST:-} ]]; then warn "DOCKER_HOST is set to $DOCKER_HOST (overrides the context)"; fi

  hdr "Docker Desktop (should be stopped and masked)"
  local en; en=$(systemctl --user is-enabled docker-desktop 2>/dev/null || true)
  if desktop_running; then
    bad "Docker Desktop is RUNNING (unit is '$en')"
    local since; since=$(systemctl --user show docker-desktop -p ActiveEnterTimestamp --value 2>/dev/null)
    [[ -n $since ]] && echo "      started: $since"
    if pgrep -f 'qemu-system.*docker-desktop' >/dev/null; then
      local rss; rss=$(ps -o rss= -p "$(pgrep -f 'qemu-system.*docker-desktop' | head -1)" 2>/dev/null)
      echo "      its qemu VM is using ~$(( ${rss:-0} / 1024 )) MB of RAM"
    fi
  else
    ok "Docker Desktop is not running"
  fi
  case $en in
    masked) ok "docker-desktop unit is masked (cannot be started by anything)" ;;
    "")     ok "docker-desktop unit not installed" ;;
    *)      warn "docker-desktop unit is '$en', not masked: its launcher can still start it" ;;
  esac
  if [[ -S $DESKTOP_SOCK ]] && ! desktop_running; then warn "stale Desktop socket left at $DESKTOP_SOCK (harmless)"; fi

  hdr "Stack host ports (${STACK_PORTS[*]})"
  local native_ports; native_ports=$(docker --context default ps --format '{{.Ports}}' 2>/dev/null)
  for p in "${STACK_PORTS[@]}"; do
    if ! ss -ltnH "sport = :$p" | grep -q .; then ok ":$p free"; continue; fi
    local owner; owner=$(ss -ltnpH "sport = :$p" 2>/dev/null | grep -o 'users:(("[^"]*",pid=[0-9]*' | head -1 | sed 's/users:(("//; s/",pid=/ pid=/')
    if grep -q ":$p->" <<<"$native_ports"; then ok ":$p held by a native container"
    elif [[ -n $owner ]]; then
      if [[ $owner == com.docker* ]]; then bad ":$p held by Docker Desktop ($owner)"; else warn ":$p held by $owner"; fi
    elif desktop_running; then bad ":$p bound, owner hidden (almost certainly Docker Desktop's forwarder)"
    else warn ":$p bound by an unknown process (sudo ss -ltnp 'sport = :$p' to see it)"; fi
  done

  hdr "Dev Container"
  local dc; dc=$(docker --context default ps -a --filter label=devcontainer.local_folder --format '{{.Names}}\t{{.Status}}' 2>/dev/null)
  if [[ -n $dc ]]; then echo "$dc" | sed 's/^/  · /'; else warn "no Dev Container found on the native engine (VS Code will build/create it)"; fi

  hdr "Memory"
  free -h | sed 's/^/  /'
  local swap_free swap_total
  read -r swap_total swap_free < <(free -m | awk '/^Swap:/ {print $2, $4}')
  if (( swap_total > 0 && swap_free * 10 < swap_total )); then
    warn "swap is >90% used. Expect sluggishness until memory is freed (stopping Desktop frees ~8 GB)"
  fi
}

fix() {
  hdr "Fixing"
  if desktop_running; then
    echo "  · stopping Docker Desktop"
    systemctl --user stop docker-desktop 2>/dev/null || true
    pkill -u "$(id -u)" -f '/opt/docker-desktop/Docker Desktop' 2>/dev/null || true
    for _ in 1 2 3 4 5 6 7 8 9 10; do desktop_running || break; sleep 1; done
    if desktop_running; then
      echo "  · still alive, killing leftover Desktop processes"
      pkill -u "$(id -u)" -f '/opt/docker-desktop/' 2>/dev/null || true
      pkill -u "$(id -u)" -f 'qemu-system.*docker-desktop' 2>/dev/null || true
      sleep 2
    fi
  fi
  if [[ $(systemctl --user is-enabled docker-desktop 2>/dev/null) != masked ]] &&
     systemctl --user cat docker-desktop >/dev/null 2>&1; then
    echo "  · masking docker-desktop so nothing can start it again"
    systemctl --user disable docker-desktop >/dev/null 2>&1 || true
    systemctl --user mask docker-desktop
  fi
  if [[ $(docker context show 2>/dev/null) != default ]]; then
    echo "  · switching CLI context to default"
    docker context use default >/dev/null
  fi
  if ! systemctl is-active --quiet docker; then
    echo "  · starting docker.service (needs sudo)"
    sudo systemctl start docker
  fi
}

diagnose
if $FIX; then
  if (( PROBLEMS == 0 )); then echo; echo "${G}Nothing to fix.${N}"; exit 0; fi
  fix
  PROBLEMS=0
  echo; echo "${B}Re-checking…${N}"
  diagnose
fi

echo
if (( PROBLEMS == 0 )); then
  echo "${G}${B}All good.${N} In VS Code: Dev Containers: Reopen in Container (or Rebuild Container)."
else
  echo "${R}${B}$PROBLEMS problem(s).${N}"
  $FIX || echo "Run:  ./scripts/docker-doctor.bash --fix"
  exit 1
fi
