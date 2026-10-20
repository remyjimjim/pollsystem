#!/usr/bin/env bash
#
# proxy-secret.bash — manage PROXY_SECRET, the secret shared by the Cloudflare
# Pages proxy and the Fly backend, and check that per-IP rate limits work.
#
# Why: every visitor reaches the backend through the Pages proxy, so the backend
# sees Cloudflare's address for everyone. The proxy passes the visitor's real IP
# as X-Client-IP, signed with X-Proxy-Secret; the backend believes it only when
# the secret matches. Without the secret on both sides, per-IP limits stay off
# (per-email limits work regardless).
#
# The secret lives in .devcontainer/proxy.secrets.env (git-ignored, mode 600).
# This script never prints it.
#
# Usage:
#   ./scripts/proxy-secret.bash generate          # create the secrets file if missing
#   ./scripts/proxy-secret.bash status            # file present? on which Fly apps?
#   ./scripts/proxy-secret.bash set staging|prod  # put it on that Fly app (restarts it)
#   ./scripts/proxy-secret.bash check staging|prod  # prove per-IP limits work end to end
#
# Order matters when turning it on:
#   1. Cloudflare dashboard → Pages project → Settings → Variables and Secrets:
#      PROXY_SECRET as a Secret, for Production AND Preview (Preview = staging).
#   2. Redeploy Pages (Deployments → latest deployment → ⋯ → Retry deployment):
#      a new variable only reaches builds made after it was saved.
#   3. ./scripts/proxy-secret.bash set staging   (then prod)
#   4. ./scripts/proxy-secret.bash check staging (then prod)
#
# `check` sends ~75 account-status checks through the site in a few seconds
# (limit: 30 a minute per IP, per backend machine). If the backend sees your
# real IP, some are refused (429) → working. If none are, the real IP isn't
# arriving → step 1 or 2 isn't done. It uses up your own IP's allowance for
# that endpoint for about a minute; nobody else is affected.

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SECRETS_FILE="$ROOT/.devcontainer/proxy.secrets.env"

if [[ -t 1 ]]; then R=$'\e[31m'; G=$'\e[32m'; Y=$'\e[33m'; B=$'\e[1m'; N=$'\e[0m'; else R= G= Y= B= N=; fi
ok()   { echo "${G}✓${N} $*"; }
warn() { echo "${Y}!${N} $*"; }
fail() { echo "${R}✗${N} $*" >&2; exit 1; }

env_for() {
  case "${1:-}" in
    staging) FLY_APP=pollsystem-backend-staging; SITE=https://staging.pollsystem.pages.dev ;;
    prod)    FLY_APP=pollsystem-backend;         SITE=https://pollsystem.pages.dev ;;
    *) fail "say which environment: staging or prod" ;;
  esac
  BACKEND="https://$FLY_APP.fly.dev"
}

load_secret() {
  [[ -f "$SECRETS_FILE" ]] || fail "no $SECRETS_FILE — run: $0 generate"
  PROXY_SECRET="$(sed -n 's/^PROXY_SECRET=//p' "$SECRETS_FILE")"
  [[ ${#PROXY_SECRET} -ge 32 ]] || fail "PROXY_SECRET in $SECRETS_FILE looks wrong (too short)"
}

cmd_generate() {
  if [[ -f "$SECRETS_FILE" ]]; then ok "already exists: $SECRETS_FILE (left unchanged)"; return; fi
  (umask 077; printf 'PROXY_SECRET=%s\n' "$(openssl rand -hex 32)" > "$SECRETS_FILE")
  ok "created $SECRETS_FILE (mode 600). Copy the value into Cloudflare Pages yourself:"
  echo "    cat $SECRETS_FILE"
}

cmd_status() {
  if [[ -f "$SECRETS_FILE" ]]; then ok "secrets file present"; else warn "no secrets file (run: $0 generate)"; fi
  for e in staging prod; do
    env_for "$e"
    if flyctl secrets list -a "$FLY_APP" 2>/dev/null | grep -q '^PROXY_SECRET\b\|[[:space:]]PROXY_SECRET[[:space:]]'; then
      ok "$e: PROXY_SECRET is set on $FLY_APP"
    else
      warn "$e: PROXY_SECRET is NOT set on $FLY_APP"
    fi
  done
  echo "  (Cloudflare Pages can't be checked from here; '$0 check <env>' tests the whole path.)"
}

cmd_set() {
  env_for "${1:-}"; load_secret
  if [[ "$1" == prod ]]; then
    warn "This sets PROXY_SECRET on PROD ($FLY_APP) and restarts its machines."
    read -r -p "  Type 'prod' to continue: " answer
    [[ "$answer" == prod ]] || fail "cancelled"
  fi
  printf 'PROXY_SECRET=%s\n' "$PROXY_SECRET" \
    | flyctl secrets import -a "$FLY_APP" 2>&1 | grep -v 'PROXY_SECRET=' | tail -3
  [[ ${PIPESTATUS[1]} -eq 0 ]] || fail "flyctl secrets import failed"
  ok "PROXY_SECRET set on $FLY_APP (value not shown)"
}

status_code() {  # $1 = base URL; one account-status check for a throwaway address
  curl -s -o /dev/null -w '%{http_code}\n' --max-time 30 -X POST -H 'Content-Type: application/json' \
    -d "{\"email\":\"proxy-check-$RANDOM$RANDOM@example.test\"}" "$1/api/auth/status"
}
export -f status_code

cmd_check() {
  env_for "${1:-}"
  echo "${B}Checking per-IP limits on $1${N} ($SITE → $BACKEND)"
  curl -sf -o /dev/null --max-time 60 "$BACKEND/actuator/health" || fail "backend not healthy: $BACKEND"

  local codes refused other
  codes="$(seq 75 | xargs -P 10 -I{} bash -c 'status_code "$0"' "$SITE")"
  refused=$(grep -c '^429$' <<<"$codes")
  other=$(grep -vcE '^(200|429)$' <<<"$codes")
  echo "  through the site: $(grep -c '^200$' <<<"$codes") OK, $refused refused, $other other"
  echo "  straight to the backend: $(status_code "$BACKEND")  (your connection's own address; informational)"

  if (( other > 0 )); then
    fail "unexpected responses — is $SITE up and proxying /api?"
  elif (( refused > 0 )); then
    ok "working: the backend sees your real IP through the proxy."
  else
    warn "NOT working: nothing was refused, so the backend isn't getting your real IP."
    echo "  Likely causes, in order:"
    echo "    1. Pages hasn't been redeployed since PROXY_SECRET was saved (Retry deployment)."
    echo "    2. PROXY_SECRET isn't set for this environment in Pages (Preview = staging)."
    echo "    3. The value differs from the Fly secret ($0 set $1 re-sends the local one)."
    echo "    4. The backend predates rate limiting (prod needs a backend deploy)."
    exit 1
  fi
}

case "${1:-}" in
  generate) cmd_generate ;;
  status)   cmd_status ;;
  set)      cmd_set "${2:-}" ;;
  check)    cmd_check "${2:-}" ;;
  -h|--help|"") sed -n '2,35p' "$0" ;;
  *) fail "unknown command: $1 (try --help)" ;;
esac
