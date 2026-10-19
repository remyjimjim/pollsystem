#!/usr/bin/env bash
# seed.bash (scripts/demo/) — start the LOCAL database over with a decently seeded demo.
#
# From scripts/pseudocode/swagger-like-db-reset.txt. Everything goes through
# the real API, the way the app does it: creator and admin access come from
# real requests that get approved, and the polls are real drafts.
#
#   1. Wipe: deletes the previous demo users (demo-…@test.local) and the e2e
#      test users (zzz…), with everything they made. Every other user (your
#      own super, admin@local.test, …) is left alone.
#   2. demo-super@test.local, who approves the requests below.
#   3. Per state (Washington, Colorado, California): 10 respondents, a member,
#      a creator and an admin, all paid members.
#   4. Per state, the creator: requests creator access for the state (approved),
#      creates an election (then adds 4 candidates in 2 districts), attaches 3
#      ballot measures, and creates 3 questionnaires. All DRAFTS.
#   5. Per state, the admin: requests creator access (approved), creates the
#      same 3 questionnaires (drafts), then requests admin access for the state
#      (approved by the super).
#
# Local only: it needs the dev endpoints (/api/dev/…), which exist only under
# the `local` profile (`BuildAndDeploy.bash local` or `local-docker`).
# Safe to re-run: each run wipes the previous one first.
#
# Sign in as any of them at http://localhost:3000/login; the link arrives in
# Mailpit (http://localhost:8025). Or use a token from /api/dev/token.
#
# Usage: scripts/demo/seed.bash        (API at http://localhost:8080)
#        API=http://host:8080 scripts/demo/seed.bash

set -euo pipefail

API="${API:-http://localhost:8080}"
PREFIX="demo-"
ELECTION_DATE="2026-11-03"
EFFECTIVE_DATE="2027-01-01"

# ── output ────────────────────────────────────────────────────────────────
if [[ -t 1 ]]; then B=$'\e[1m'; G=$'\e[32m'; R=$'\e[31m'; D=$'\e[2m'; N=$'\e[0m'; else B= G= R= D= N=; fi
step() { printf '\n%s== %s%s\n' "$B" "$*" "$N"; }
ok()   { printf '  %s✓%s %s\n' "$G" "$N" "$*"; }
die()  { printf '%s✗ %s%s\n' "$R" "$*" "$N" >&2; exit 1; }

command -v curl >/dev/null || die "curl is needed"
command -v python3 >/dev/null || die "python3 is needed"

# ── API helpers ───────────────────────────────────────────────────────────
# call METHOD PATH [TOKEN] [JSON] → prints the response body; dies on non-2xx
# with the server's message.
call() {
    local method="$1" path="$2" token="${3:-}" body="${4:-}" out code
    local args=(-sS -X "$method" -w '\n%{http_code}' "$API$path")
    [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
    [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' --data "$body")
    out="$(curl "${args[@]}")" || die "$method $path: no answer from $API (is the local stack up?)"
    code="${out##*$'\n'}"; out="${out%$'\n'*}"
    if [[ "$code" != 2* ]]; then
        local msg; msg="$(printf '%s' "$out" | python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("message",""))
except Exception: print("")' 2>/dev/null)"
        die "$method $path → HTTP $code ${msg:-$out}"
    fi
    printf '%s' "$out"
}
# json FIELD: read a field from the JSON on stdin (dotted paths, e.g. a.b).
json() { python3 -c 'import json,sys
v=json.load(sys.stdin)
for k in sys.argv[1].split("."): v=v[int(k)] if isinstance(v,list) else v[k]
print(v)' "$1"; }

token_for() { call POST "/api/dev/token?email=$1" | json token; }

# Fail early with a clear message if the dev endpoints aren't there.
curl -sS -o /dev/null "$API/actuator/health" 2>/dev/null || die "the backend isn't answering at $API (start the local stack first)"

# ── 1. wipe ───────────────────────────────────────────────────────────────
step "Wiping previous demo and e2e test users"
for p in "$PREFIX" zzz; do
    n="$(call POST "/api/dev/reset-test-users?emailPrefix=$p" | json deleted)"
    ok "$p… users deleted: $n"
done

# ── 2. the approver ───────────────────────────────────────────────────────
step "Creating the approver"
SUPER="${PREFIX}super@test.local"
call POST "/api/dev/seed-user?emailPrefix=$PREFIX&access=SUPER&zipcode=98264&email=$SUPER" >/dev/null
SUPER_TOKEN="$(token_for "$SUPER")"
ok "$SUPER (SUPER)"

STATES_JSON="$(call GET /api/states)"
state_id() { printf '%s' "$STATES_JSON" | python3 -c 'import json,sys
n=sys.argv[1]; print(next(s["id"] for s in json.load(sys.stdin) if s["name"]==n))' "$1"; }

# ── 3. users ──────────────────────────────────────────────────────────────
# seed EMAIL_NAME ZIP → demo-EMAIL_NAME@test.local, a paid member.
seed() {
    call POST "/api/dev/seed-user?emailPrefix=$PREFIX&access=USER&zipcode=$2&email=$PREFIX$1@test.local" >/dev/null
}
step "Creating users"
declare -A RESPONDENT_ZIPS=(
    [wash]="98264 99156 98501 99201 98201 98201 98264 98264 98248 99156"
    [colo]="80204 80204 80204 80204 80211 80211 80211 80211 80211 80211"
    [cali]="90001 90011 90201 90301 90401 90501 90002 90003 90004 90004"
)
declare -A MEMBER_ZIP=([wash]=98225 [colo]=80206 [cali]=90001)
declare -A CREATOR_ZIP=([wash]=98264 [colo]=80210 [cali]=90002)
declare -A ADMIN_ZIP=([wash]=98264 [colo]=80212 [cali]=90003)
for st in wash colo cali; do
    i=0
    for z in ${RESPONDENT_ZIPS[$st]}; do i=$((i + 1)); seed "respondent$i-$st" "$z"; done
    seed "user-$st" "${MEMBER_ZIP[$st]}"
    seed "creator-$st" "${CREATOR_ZIP[$st]}"
    seed "admin-$st" "${ADMIN_ZIP[$st]}"
    ok "$st: 10 respondents, user-$st, creator-$st, admin-$st"
done

# ── access ────────────────────────────────────────────────────────────────
# Requested as the user, approved as the super: the real request flow.
grant_creator() {  # EMAIL STATE_ID STATE_NAME
    local tok id
    tok="$(token_for "$1")"
    id="$(call POST /api/creator-requests "$tok" \
        "{\"pollTypeIds\":[1,2,3],\"scopeLevel\":\"STATE\",\"regionIds\":[$2],\"reason\":\"Demo seed: creating polls for $3\"}" | json id)"
    call POST /api/admin/creator-requests/batch-approve "$SUPER_TOKEN" "{\"requestIds\":[$id]}" >/dev/null
    ok "$1: creator access for $3 (request $id approved)"
}
grant_admin() {  # EMAIL STATE_ID STATE_NAME
    local tok id
    tok="$(token_for "$1")"
    id="$(call POST /api/admin-requests "$tok" \
        "{\"scopeLevel\":\"STATE\",\"regionIds\":[$2],\"reason\":\"Demo seed: administering $3\"}" | json id)"
    call POST /api/super/admin-requests/batch-approve "$SUPER_TOKEN" "{\"requestIds\":[$id]}" >/dev/null
    ok "$1: admin access for $3 (request $id approved)"
}

# ── polls (all drafts) ────────────────────────────────────────────────────
# JSON-escape a string (titles contain "/" and spaces).
q() { python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$1"; }

questionnaire() {  # TOKEN STATE_ID TITLE TOPIC
    local questions
    case "$4" in
        vaccines) questions='[{"text":"Should childhood vaccines be required for public school?"},{"text":"Should vaccine makers be liable for injuries?"},{"text":"Should vaccination records stay private?"}]' ;;
        money)    questions='[{"text":"Should corporate donations to campaigns be capped?"},{"text":"Should all political donations be public?"},{"text":"Should elections be publicly funded?"}]' ;;
        food)     questions='[{"text":"Should glyphosate be banned on food crops?"},{"text":"Should foods grown with glyphosate be labelled?"},{"text":"Would you pay more for glyphosate-free food?"}]' ;;
    esac
    call POST /api/polls/questionnaires "$1" \
        "{\"pollTypeId\":2,\"title\":$(q "$3"),\"summary\":$(q "Demo questionnaire: $3"),\"closeDate\":null,\"questions\":$questions,\"scopeLevel\":\"STATE\",\"regionIds\":[$2]}" | json id
}

creator_polls() {  # EMAIL STATE_ID STATE_NAME ELECTION_TITLE MEASURE_LABEL QUESTIONNAIRE_LABEL
    local tok eid
    tok="$(token_for "$1")"
    # The election: created as a draft, then edited to add the candidates.
    local base="\"pollTypeId\":1,\"title\":$(q "$4"),\"date\":\"$ELECTION_DATE\",\"scopeLevel\":\"STATE\",\"regionIds\":[$2],\"closeDate\":null"
    eid="$(call POST /api/polls/elections "$tok" "{$base,\"candidates\":[]}" | json id)"
    call PUT "/api/polls/elections/$eid" "$tok" "{$base,\"candidates\":[
        {\"name\":\"Jim James\",\"affiliation\":\"Red\",\"officeName\":\"District 1\"},
        {\"name\":\"Judy James\",\"affiliation\":\"Blue\",\"officeName\":\"District 1\"},
        {\"name\":\"Barney Rubble\",\"affiliation\":\"Red\",\"officeName\":\"District 2\"},
        {\"name\":\"Fred Flintstone\",\"affiliation\":\"Blue\",\"officeName\":\"District 2\"}]}" >/dev/null
    ok "$1: election \"$4\" (draft, 4 candidates)"
    local n=0 m
    for m in "No nukes" "No sales tax" "No property tax"; do
        n=$((n + 1))
        call POST /api/polls/ballot-measures "$tok" \
            "{\"pollTypeId\":3,\"electionId\":$eid,\"title\":$(q "$5 Measure $n $m"),\"summary\":$(q "A Yes vote means: $m. A No vote keeps things as they are."),\"effectiveDate\":\"$EFFECTIVE_DATE\",\"closeDate\":null}" >/dev/null
    done
    ok "$1: 3 ballot measures on \"$4\" (drafts)"
    questionnaires "$tok" "$2" "$6" "$1"
}

questionnaires() {  # TOKEN STATE_ID LABEL EMAIL
    questionnaire "$1" "$2" "$3 Questionnaire 1 vaccines" vaccines >/dev/null
    questionnaire "$1" "$2" "$3 Questionnaire 2 money in politics" money >/dev/null
    questionnaire "$1" "$2" "$3 Questionnaire 3 Glyphosate/Food" food >/dev/null
    ok "$4: 3 questionnaires (drafts)"
}

# ── 4–5. per state ────────────────────────────────────────────────────────
seed_state() {  # KEY STATE_NAME MEASURE_LABEL QUESTIONNAIRE_LABEL
    local sid creator admin
    sid="$(state_id "$2")"
    creator="${PREFIX}creator-$1@test.local"
    admin="${PREFIX}admin-$1@test.local"
    step "$2"
    grant_creator "$creator" "$sid" "$2"
    creator_polls "$creator" "$sid" "$2" "MidTerm Election $2" "$3" "$4"
    grant_creator "$admin" "$sid" "$2"
    questionnaires "$(token_for "$admin")" "$sid" "$4" "$admin"
    grant_admin "$admin" "$sid" "$2"
}
seed_state wash Washington Wash Wash
seed_state colo Colorado Colorado Colorado
seed_state cali California CA California

# ── summary ───────────────────────────────────────────────────────────────
step "Done"
cat <<EOF
  Approver:   ${SUPER} (SUPER)
  Per state (wash / colo / cali), all ${PREFIX}…@test.local, paid members:
    ${PREFIX}creator-<st>        creator for the state: 1 election (4 candidates, 3 measures), 3 questionnaires
    ${PREFIX}admin-<st>          admin + creator for the state: 3 questionnaires
    ${PREFIX}user-<st>           member
    ${PREFIX}respondent1..10-<st> members
  All polls are drafts. Sign in at http://localhost:3000/login (links land in http://localhost:8025).
  ${D}Re-run any time: it wipes this demo (and the e2e zzz users) first.${N}
EOF
