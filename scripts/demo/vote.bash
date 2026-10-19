#!/usr/bin/env bash
# vote.bash (scripts/demo/) — watch a member sign in and answer a questionnaire, in a
# new headed browser, slowly. Runs frontend/e2e/demo/user-votes-on-a-poll.spec.ts.
#
# Run on the HOST (a visible browser needs your screen), with the local stack up
# (BuildAndDeploy.bash local-docker or local) and demo users seeded
# (scripts/demo/seed.bash). Needs Node and Playwright's Chromium on the host
# (once: cd frontend && npx playwright install chromium).
#
# Usage:
#   scripts/demo/vote.bash <email> [options]
#     --poll "<title>"     questionnaire title (default: the Seahawks one)
#     --answer Yes|No      answer for every question (default Yes)
#     --pause <ms>         pause after each action (default 3000)
#     --login-delay <ms>   wait between signing in and Find polls (default 4000)
#
# Example:
#   scripts/demo/vote.bash demo-user-wash@test.local
#   scripts/demo/vote.bash demo-respondent3-wash@test.local --answer No --pause 1500

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

[[ $# -ge 1 && "$1" != -* ]] || { sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
export E2E_USER="$1"; shift
while [[ $# -gt 0 ]]; do
  case "$1" in
    --poll)        export E2E_POLL_TITLE="${2:?--poll needs a title}"; shift ;;
    --answer)      [[ "${2:-}" == Yes || "${2:-}" == No ]] || { echo "--answer is Yes or No" >&2; exit 2; }
                   export E2E_ANSWER="$2"; shift ;;
    --pause)       export E2E_STEP_PAUSE_MS="${2:?--pause needs milliseconds}"; shift ;;
    --login-delay) export E2E_LOGIN_DELAY_MS="${2:?--login-delay needs milliseconds}"; shift ;;
    -h|--help)     sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $1 (try --help)" >&2; exit 2 ;;
  esac
  shift
done

curl -sf -o /dev/null http://localhost:3000 || { echo "The frontend isn't up at http://localhost:3000 (start the local stack first)" >&2; exit 1; }
curl -sf -o /dev/null http://localhost:8025/api/v1/messages || { echo "Mailpit isn't up at http://localhost:8025" >&2; exit 1; }

cd "$ROOT/frontend"
echo "Signing in as $E2E_USER and answering \"${E2E_POLL_TITLE:-Will the Seahawks win the super bowl again in 2026/7?}\" (${E2E_ANSWER:-Yes})…"
DEMO_E2E=1 npx playwright test demo/user-votes-on-a-poll --headed --workers=1 --reporter=list
