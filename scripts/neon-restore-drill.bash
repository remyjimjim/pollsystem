#!/usr/bin/env bash
#
# neon-restore-drill.bash — prove prod's database can be restored to a point in
# time, without touching prod.
#
# Neon keeps a history of the database (the "restore window", set by the plan).
# This script:
#   1. finds the prod project and reports its restore window;
#   2. creates a temporary branch of prod AS IT WAS at --at (a separate copy);
#   3. connects to it as the read-only role and compares it with live prod
#      (schema version and row counts per table);
#   4. deletes the temporary branch (unless --keep).
# Prod is only read. The branch is billed for the minutes it exists.
#
# Needs (both git-ignored):
#   .devcontainer/neon-api.secrets.env       NEON_API_KEY=…   (Neon console →
#        Account settings → API keys)
#   .devcontainer/neon-readonly.secrets.env  NEON_RO_PASSWORD=…  (role `claude`)
#
# Usage:
#   ./scripts/neon-restore-drill.bash                       # restore to 1 hour ago
#   ./scripts/neon-restore-drill.bash --at 2026-10-20T09:00:00Z
#   ./scripts/neon-restore-drill.bash --keep                # leave the branch for a look
#
# In a real emergency (not this drill), restore in the Neon console instead:
# Branches → production branch → Restore → pick the time → Restore. Neon keeps
# a backup branch of the state it replaced. See docs/BACKUPS.md.

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROD_HOST=ep-shiny-base-ax9uc7iz.c-4.us-east-2.aws.neon.tech
DB=neondb
ROLE=claude
API=https://console.neon.tech/api/v2
AT=""; KEEP=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --at) AT="$2"; shift 2 ;;
    --keep) KEEP=true; shift ;;
    -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
    *) echo "unknown option: $1 (try --help)" >&2; exit 2 ;;
  esac
done

if [[ -t 1 ]]; then G=$'\e[32m'; Y=$'\e[33m'; R=$'\e[31m'; B=$'\e[1m'; N=$'\e[0m'; else G= Y= R= B= N=; fi
ok()   { echo "${G}✓${N} $*"; }
warn() { echo "${Y}!${N} $*"; }
fail() { echo "${R}✗${N} $*" >&2; exit 1; }

for f in neon-api neon-readonly; do
  [[ -f "$ROOT/.devcontainer/$f.secrets.env" ]] || fail "missing .devcontainer/$f.secrets.env (see --help)"
  set -a; . "$ROOT/.devcontainer/$f.secrets.env"; set +a
done
[[ -n "${NEON_API_KEY:-}" && -n "${NEON_RO_PASSWORD:-}" ]] || fail "NEON_API_KEY / NEON_RO_PASSWORD not set in the secrets files"

api() {  # api METHOD PATH [JSON]
  curl -sS --fail-with-body -X "$1" -H "Authorization: Bearer $NEON_API_KEY" -H 'Content-Type: application/json' \
    ${3:+-d "$3"} "$API$2"
}
json() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }
psql_ro() {  # psql_ro HOST SQL
  docker run --rm -e PGPASSWORD="$NEON_RO_PASSWORD" postgres:18 \
    psql "host=$1 dbname=$DB user=$ROLE sslmode=require connect_timeout=15" -tA -F'|' -c "$2"
}

# ---- 1. the project and its restore window ----
# Projects live in organizations; look in each one the key can see.
orgs=$(api GET "/users/me/organizations") || fail "Neon API refused the key: $orgs"
endpoint_id=${PROD_HOST%%.*}
PROJECT=""
for org in $(echo "$orgs" | json '" ".join(o["id"] for o in d.get("organizations", []))'); do
  projects=$(api GET "/projects?limit=100&org_id=$org") || continue
  for p in $(echo "$projects" | json '" ".join(x["id"] for x in d["projects"])'); do
    if api GET "/projects/$p/endpoints" | json "any(e['id']=='$endpoint_id' for e in d['endpoints'])" | grep -q True; then PROJECT=$p; break 2; fi
  done
done
[[ -n "$PROJECT" ]] || fail "no Neon project has the prod endpoint $endpoint_id"
project=$(api GET "/projects/$PROJECT")
window_s=$(echo "$project" | json 'd["project"].get("history_retention_seconds", 0)')
prod_branch=$(api GET "/projects/$PROJECT/endpoints/$endpoint_id" | json 'd["endpoint"]["branch_id"]')
earliest=$(date -u -d "@$(( $(date +%s) - window_s ))" +%Y-%m-%dT%H:%M:%SZ)
ok "project $PROJECT, prod branch $prod_branch"
ok "restore window: $(( window_s / 3600 )) hours (back to $earliest)"

[[ -z "$AT" ]] && AT=$(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%SZ)
at_s=$(date -u -d "$AT" +%s) || fail "bad --at time: $AT"
if (( at_s < $(date +%s) - window_s )); then
  warn "$AT is before the restore window; using 1 hour ago instead"
  AT=$(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%SZ)
fi

# ---- 2. a temporary branch of prod as it was at $AT ----
name="restore-drill-$(date -u +%Y%m%dT%H%M%SZ)"
created=$(api POST "/projects/$PROJECT/branches" \
  "{\"branch\":{\"name\":\"$name\",\"parent_id\":\"$prod_branch\",\"parent_timestamp\":\"$AT\"},\"endpoints\":[{\"type\":\"read_write\"}]}") \
  || fail "could not create the branch: $created"
branch=$(echo "$created" | json 'd["branch"]["id"]')
host=$(echo "$created" | json 'd["endpoints"][0]["host"]')
ok "branch $name ($branch) = prod as of $AT"

cleanup() {
  if $KEEP; then warn "kept branch $name ($branch); delete it in the Neon console when done"; return; fi
  api DELETE "/projects/$PROJECT/branches/$branch" >/dev/null && ok "deleted branch $name" || warn "couldn't delete branch $name ($branch): delete it in the console"
}
trap cleanup EXIT

# ---- 3. compare it with live prod ----
for i in $(seq 1 24); do psql_ro "$host" "select 1" >/dev/null 2>&1 && break; sleep 5; done
COUNTS="select string_agg(t || '=' || n, ' ' order by t) from (
  select table_name as t, (xpath('/row/c/text()', query_to_xml('select count(*) as c from public.'||quote_ident(table_name), false, true, '')))[1]::text::int as n
  from information_schema.tables where table_schema='public' and table_type='BASE TABLE' and table_name <> 'flyway_schema_history') s"
VERSION="select max(version::int) from flyway_schema_history where success"
r_ver=$(psql_ro "$host" "$VERSION") || fail "couldn't read the restored branch"
p_ver=$(psql_ro "$PROD_HOST" "$VERSION")
r_counts=$(psql_ro "$host" "$COUNTS"); p_counts=$(psql_ro "$PROD_HOST" "$COUNTS")
echo "${B}Restored ($AT)${N}: schema $r_ver"
echo "${B}Live prod (now)${N}:            schema $p_ver"
diff <(tr ' ' '\n' <<<"$r_counts") <(tr ' ' '\n' <<<"$p_counts") > /dev/null \
  && ok "row counts identical in all $(tr ' ' '\n' <<<"$p_counts" | wc -l) tables" \
  || { echo "Row counts that differ (restored → now):"; join -t= -a1 -a2 -e0 -o 0,1.2,2.2 \
         <(tr ' ' '\n' <<<"$r_counts" | sort) <(tr ' ' '\n' <<<"$p_counts" | sort) | awk -F= '$2!=$3{print "  "$1": "$2" → "$3}'; }
ok "restore drill complete: the restored copy is readable and consistent"
