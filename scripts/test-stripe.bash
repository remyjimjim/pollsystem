#!/usr/bin/env bash
#
# test-stripe.bash — run the opt-in REAL Stripe test-mode e2e
# (frontend/e2e/stripe) on the host, end to end:
#
#   1. Your Stripe TEST secret key (sk_test_…): $STRIPE_API_KEY, else the OS
#      keychain (service=pollsystem-stripe-test account=STRIPE_API_KEY), else
#      a hidden prompt (offers to save it to the keychain). Never printed.
#      Anything but sk_test_… is refused, so live keys can't be used.
#   2. The test membership price: $STRIPE_PRICE_ID, else found by lookup key
#      `pollsystem_membership_test`, else created once ($10/month).
#   3. Pauses the Docker backend/frontend (ports 8080/3000); Postgres and
#      Mailpit keep running.
#   4. `stripe listen` forwards webhooks to localhost:8080/webhooks/stripe
#      (its whsec_… secret is read automatically).
#   5. Starts the host stack (BuildAndDeploy.bash local) with
#      APP_PAYMENTS_PROVIDER=stripe and waits until it's up.
#   6. Runs `STRIPE_E2E=1 npx playwright test stripe`.
#   7. Stops what it started and restarts the Docker containers it paused.
#
# Usage:
#   ./scripts/test-stripe.bash [--headed] [--keep] [-h|--help]
#     --headed   watch the browser
#     --keep     leave stripe listen + the host stack running afterwards
#                (Ctrl-C to stop; Docker containers stay paused)
#
# Needs (on the host, not the Dev Container): the Stripe CLI (`stripe login`
# once), docker, node/npx, curl, python3; secret-tool for the keychain.
# Logs: a temp folder, printed at the start.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

KEYCHAIN_SERVICE="pollsystem-stripe-test"
PRICE_LOOKUP_KEY="pollsystem_membership_test"
WEBHOOK_URL="localhost:8080/webhooks/stripe"
# The events StripeWebhookService handles (newer Stripe CLIs require a list).
WEBHOOK_EVENTS="checkout.session.completed,customer.subscription.created,customer.subscription.updated,customer.subscription.deleted,invoice.paid,invoice.payment_failed"

if [[ -t 1 ]]; then
  C_BLUE=$'\033[34m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_RED=$'\033[31m'; C_OFF=$'\033[0m'
else
  C_BLUE=''; C_GREEN=''; C_YELLOW=''; C_RED=''; C_OFF=''
fi
info() { printf '%s==>%s %s\n' "$C_BLUE" "$C_OFF" "$*"; }
ok()   { printf '%s ✓ %s%s\n' "$C_GREEN" "$*" "$C_OFF"; }
warn() { printf '%s ! %s%s\n' "$C_YELLOW" "$*" "$C_OFF"; }
die()  { printf '%s ✗ %s%s\n' "$C_RED" "$*" "$C_OFF" >&2; exit 1; }

usage() { sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'; exit 0; }
# On failure, show the end of a log right away (it's in a temp folder).
show_tail() { echo "----- last lines of $1 -----"; tail -n 30 "$1" 2>/dev/null || true; echo "-----"; }

HEADED=""; KEEP=0
for arg in "$@"; do
  case "$arg" in
    --headed) HEADED="--headed" ;;
    --keep) KEEP=1 ;;
    -h|--help|-help) usage ;;
    *) die "Unknown option '$arg' (try --help)" ;;
  esac
done

# --- preflight ---------------------------------------------------------------
if [[ -f /.dockerenv || -n "${REMOTE_CONTAINERS:-}" || -n "${CODESPACES:-}" ]]; then
  die "Run this on the host, not inside the Dev Container (it needs the Stripe CLI, browsers and your keychain)."
fi
for c in stripe docker npx curl python3; do
  command -v "$c" >/dev/null 2>&1 || die "Missing '$c'. Stripe CLI: https://stripe.com/docs/stripe-cli"
done

LOGS="$(mktemp -d -t test-stripe.XXXXXX)"
info "Logs: $LOGS"

# --- 1. the test secret key --------------------------------------------------
key="${STRIPE_API_KEY:-}"
if [[ -z "$key" ]] && command -v secret-tool >/dev/null 2>&1; then
  key="$(secret-tool lookup service "$KEYCHAIN_SERVICE" account STRIPE_API_KEY 2>/dev/null || true)"
  [[ -n "$key" ]] && ok "Stripe test key from the keychain"
fi
if [[ -z "$key" ]]; then
  read -rsp "Stripe TEST secret key (sk_test_…, input hidden): " key; echo
  if [[ "$key" == sk_test_* ]] && command -v secret-tool >/dev/null 2>&1; then
    read -rp "Save it to the keychain for next time? [y/N] " yn
    if [[ "$yn" =~ ^[Yy]$ ]]; then
      printf '%s' "$key" | secret-tool store --label="pollsystem Stripe test key" service "$KEYCHAIN_SERVICE" account STRIPE_API_KEY
      ok "Saved (service=$KEYCHAIN_SERVICE account=STRIPE_API_KEY)"
    fi
  fi
fi
[[ "$key" == sk_test_* ]] || die "That isn't a Stripe TEST secret key (it must start with sk_test_). Live keys are refused."
export STRIPE_API_KEY="$key"
unset key

# --- 2. the test price -------------------------------------------------------
# Stripe's REST API via curl; the key goes in on stdin, so it never shows in
# the process list.
stripe_api() { # METHOD path [curl -d args…] -> JSON on stdout
  local method="$1" path="$2"; shift 2
  printf 'user = "%s:"\n' "$STRIPE_API_KEY" \
    | curl -sS -K - -X "$method" "https://api.stripe.com/v1/$path" "$@" 2>>"$LOGS/stripe-api.err"
}
json_field() { python3 -c "import json,sys; d=json.load(sys.stdin); $1"; }
price="${STRIPE_PRICE_ID:-}"
if [[ -z "$price" ]]; then
  found="$(stripe_api GET prices -G -d "lookup_keys[]=$PRICE_LOOKUP_KEY" -d limit=1)" \
    || die "Couldn't reach Stripe (see $LOGS/stripe-api.err)"
  if printf '%s' "$found" | json_field 'sys.exit(0 if "error" in d else 1)' 2>/dev/null; then
    die "Stripe rejected the request: $(printf '%s' "$found" | json_field 'print(d["error"].get("message", "unknown error"))')"
  fi
  price="$(printf '%s' "$found" | json_field 'print(d["data"][0]["id"] if d.get("data") else "")')"
fi
if [[ -z "$price" ]]; then
  info "No test price yet; creating 'Poll System membership (test)' at \$10/month…"
  product="$(stripe_api POST products -d "name=Poll System membership (test)" | json_field 'print(d["id"])')" \
    || die "Creating the product failed (see $LOGS/stripe-api.err)"
  price="$(stripe_api POST prices -d "product=$product" -d unit_amount=1000 -d currency=usd \
    -d "recurring[interval]=month" -d "lookup_key=$PRICE_LOOKUP_KEY" | json_field 'print(d["id"])')" \
    || die "Creating the price failed (see $LOGS/stripe-api.err)"
fi
export STRIPE_PRICE_ID="$price"
ok "Test price: $STRIPE_PRICE_ID"

# --- 3. pause the Docker app containers (ports 8080 / 3000) ------------------
PAUSED=()
for svc in backend frontend; do
  if [[ -n "$(docker compose --profile app ps -q --status running "$svc" 2>/dev/null)" ]]; then PAUSED+=("$svc"); fi
done
if (( ${#PAUSED[@]} )); then
  info "Pausing Docker ${PAUSED[*]} (ports 8080/3000)…"
  docker compose --profile app stop "${PAUSED[@]}" >/dev/null
fi

# Job control: every background job gets its own process group whose id is
# its $!, so we can watch it and stop it (and all its children) reliably.
# (setsid could fork, leaving $! pointing at a process that had already exited.)
set -m
LISTEN_PID=""; STACK_PID=""
cleanup() {
  trap - INT TERM EXIT
  if (( KEEP )) && [[ "${1:-}" != "interrupted" ]]; then return; fi
  echo
  info "Cleaning up…"
  [[ -n "$STACK_PID"  ]] && kill -TERM -- -"$STACK_PID"  2>/dev/null || true
  [[ -n "$LISTEN_PID" ]] && kill -TERM -- -"$LISTEN_PID" 2>/dev/null || true
  wait 2>/dev/null || true
  if (( ${#PAUSED[@]} )); then
    info "Restarting Docker ${PAUSED[*]}…"
    docker compose --profile app start "${PAUSED[@]}" >/dev/null || warn "Couldn't restart them: ./scripts/BuildAndDeploy.bash local-docker"
  fi
  ok "Done. Logs: $LOGS"
}
trap 'cleanup interrupted' INT TERM
trap cleanup EXIT

# --- 4. stripe listen ----------------------------------------------------------
STRIPE_WEBHOOK_SECRET="$(stripe listen --api-key "$STRIPE_API_KEY" --events "$WEBHOOK_EVENTS" --print-secret 2>>"$LOGS/stripe-cli.err")"
[[ "$STRIPE_WEBHOOK_SECRET" == whsec_* ]] || die "Couldn't get a webhook secret from 'stripe listen' (see $LOGS/stripe-cli.err)"
export STRIPE_WEBHOOK_SECRET
info "Forwarding Stripe webhooks to $WEBHOOK_URL (log: $LOGS/stripe-listen.log)…"
stripe listen --api-key "$STRIPE_API_KEY" --events "$WEBHOOK_EVENTS" --forward-to "$WEBHOOK_URL" >"$LOGS/stripe-listen.log" 2>&1 &
LISTEN_PID=$!
sleep 3
if ! kill -0 "$LISTEN_PID" 2>/dev/null; then
  show_tail "$LOGS/stripe-listen.log"
  die "'stripe listen' stopped right away (full log: $LOGS/stripe-listen.log)"
fi

# --- 5. the host stack with real Stripe ----------------------------------------
info "Starting the host stack with APP_PAYMENTS_PROVIDER=stripe (log: $LOGS/stack.log)…"
APP_PAYMENTS_PROVIDER=stripe SKIP_WATCH=1 ./scripts/BuildAndDeploy.bash local >"$LOGS/stack.log" 2>&1 </dev/null &
STACK_PID=$!
wait_for() { # label, seconds, url
  local i
  for ((i = 0; i < $2; i += 2)); do
    curl -sf -o /dev/null "$3" && { ok "$1 is up"; return 0; }
    if ! kill -0 "$STACK_PID" 2>/dev/null; then
      show_tail "$LOGS/stack.log"
      die "The stack stopped while starting (full log: $LOGS/stack.log)"
    fi
    sleep 2
  done
  show_tail "$LOGS/stack.log"
  die "$1 didn't come up within $2s (full log: $LOGS/stack.log)"
}
wait_for "Backend" 300 "http://localhost:8080/actuator/health"
wait_for "Frontend" 120 "http://localhost:3000/"

# --- 6. the tests --------------------------------------------------------------
info "Running the Stripe test-mode e2e${HEADED:+ (headed)}…"
set +e
(cd frontend && STRIPE_E2E=1 npx playwright test stripe $HEADED)
status=$?
set -e
if (( status == 0 )); then ok "Stripe test-mode e2e passed"; else warn "Stripe test-mode e2e FAILED (exit $status). Webhooks: $LOGS/stripe-listen.log"; fi

if (( KEEP )); then
  info "--keep: stripe listen + the host stack keep running. Ctrl-C to stop (Docker containers stay paused)."
  wait "$STACK_PID" || true
fi
exit "$status"
