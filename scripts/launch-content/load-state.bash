#!/usr/bin/env bash
#
# load-state.bash: create (and optionally publish) states' midterm polls from
# the content files build-state.py made, in one environment.
#
# Usage:
#   scripts/launch-content/load-state.bash local|staging|prod XX [XX ...] [--publish]
#
# Order per state: the statewide election (with its measures) first, then the
# district ballots, which include it. Rerunnable: existing polls are skipped.
# --publish then publishes the state's draft midterm elections, statewide
# first (its measures go live with it).
#
# Tokens (git-ignored): .devcontainer/{local-demo-super,staging-super,prod-super}-token.secrets.env

set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
ENV="${1:?local|staging|prod}"; shift
PUBLISH=false; STATES=()
for a in "$@"; do [[ "$a" == --publish ]] && PUBLISH=true || STATES+=("${a^^}"); done
case "$ENV" in
  local)   API=http://localhost:8080;                     TOKEN=.devcontainer/local-demo-super-token.secrets.env; NET=(--network host) ;;
  staging) API=https://pollsystem-backend-staging.fly.dev; TOKEN=.devcontainer/staging-super-token.secrets.env;   NET=() ;;
  prod)    API=https://pollsystem-backend.fly.dev;         TOKEN=.devcontainer/prod-super-token.secrets.env;      NET=() ;;
  *) echo "unknown environment: $ENV" >&2; exit 2 ;;
esac
[[ -f "$TOKEN" ]] || { echo "missing $TOKEN" >&2; exit 1; }
py() { docker run --rm "${NET[@]}" -v "$ROOT":/repo:ro -w /repo python:3.12-slim python -I -B "$@"; }

fail=0
for st in "${STATES[@]}"; do
  lc="${st,,}"
  for part in statewide ballot house; do
    f="scripts/launch-content/$lc-2026-$part.json"
    [[ -f "$f" ]] || continue
    out=$(py scripts/launch-content/create-polls.py "$f" --api "$API" --token-file "$TOKEN" 2>&1); rc=$?
    echo "$st $part: $(grep -c '^✓ election' <<<"$out") elections, $(grep -c '^✓ ballot measure' <<<"$out") measures created, $(grep -c '^·' <<<"$out") already there$( ((rc)) && echo " ✗ $(grep '✗' <<<"$out" | head -1)")"
    ((rc)) && fail=1
  done
done

if $PUBLISH && (( ! fail )); then
  names=$(for st in "${STATES[@]}"; do python3 -c "import json,sys; print(json.load(open('docs/launch-content/research/$st.json'))['state'])"; done)
  ids=$(py -c "
import json, sys, urllib.request
req = urllib.request.Request('$API/api/creator/polls', headers={'Authorization': 'Bearer ' + open('$TOKEN').read().strip()})
rows = json.load(urllib.request.urlopen(req, timeout=60))
names = '''$names'''.split(chr(10))
import re
# Exact title shapes, so 'Virginia' never matches 'West Virginia'.
def ours(t): return any(n and (t.startswith('2026 Midterm · ' + n + ':') or t == '2026 Midterm ' + n or re.fullmatch(re.escape('2026 Midterm ' + n) + r' District \\d+', t)) for n in names)
mine = [r for r in rows if r['type'] == 'Election' and r['status'] == 'DRAFT' and ours(r['title'])]
mine.sort(key=lambda r: (' District ' in r['title'], r['id']))   # statewide first
print(' '.join(str(r['id']) for r in mine))")
  if [[ -n "$ids" ]]; then
    out=$(py scripts/launch-content/create-polls.py --api "$API" --token-file "$TOKEN" --publish-election $ids 2>&1); rc=$?
    echo "published $(grep -c '^✓ published' <<<"$out") elections$( ((rc)) && echo " ✗ $(grep '✗' <<<"$out" | head -1)")"
    ((rc)) && fail=1
  else
    echo "nothing to publish"
  fi
fi
exit $fail
