#!/usr/bin/env bash
#
# backup-prod.bash — a daily backup of prod's database that we keep ourselves,
# on top of Neon's restore window (decided 2026-10-21).
#
# Takes ONE consistent snapshot (a single read-only transaction) as the
# read-only role `claude`, and writes assets/data-backups/prod-<UTC time>.tar.gz:
#   schema.sql      the table definitions (pg_dump --schema-only, schema public)
#   <table>.csv     every table's rows, with a header line
#   manifest.txt    when, schema version, and each table's row count
# Then deletes backups older than --keep-days (never the newest one).
#
# Backups hold personal data: the folder is git-ignored and files are readable
# only by you. Prod is only read.
#
# Needs: docker, and .devcontainer/neon-readonly.secrets.env (NEON_RO_PASSWORD).
#
# Usage:
#   ./scripts/backup-prod.bash                  # back up now, keep 14 days
#   ./scripts/backup-prod.bash --keep-days 30
# Check a backup restores: ./scripts/restore-backup-check.bash [file]
# Run daily: ./scripts/install-backup-timer.bash (on the host, once)

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/assets/data-backups"
KEEP_DAYS=14
HOST=ep-shiny-base-ax9uc7iz.c-4.us-east-2.aws.neon.tech
CONN="host=$HOST dbname=neondb user=claude sslmode=require connect_timeout=20"
IMAGE=postgres:18
while [[ $# -gt 0 ]]; do
  case "$1" in
    --keep-days) KEEP_DAYS="$2"; shift 2 ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) echo "unknown option: $1 (try --help)" >&2; exit 2 ;;
  esac
done

log() { echo "$(date -u +%FT%TZ) $*" | tee -a "$OUT_DIR/backup.log"; }
fail() { log "FAILED: $*"; exit 1; }

umask 077
mkdir -p "$OUT_DIR" && chmod 700 "$OUT_DIR"
[[ -f "$ROOT/.devcontainer/neon-readonly.secrets.env" ]] || fail "missing .devcontainer/neon-readonly.secrets.env"
set -a; . "$ROOT/.devcontainer/neon-readonly.secrets.env"; set +a

stamp=$(date -u +%Y%m%dT%H%M%SZ)
work=$(mktemp -d "$OUT_DIR/.work-$stamp-XXXX")
trap 'rm -rf "$work"' EXIT

pg() {  # run a postgres client in docker, as you, with the work dir at /out
  docker run --rm -i --user "$(id -u):$(id -g)" -e PGPASSWORD="$NEON_RO_PASSWORD" -v "$work:/out" "$IMAGE" "$@"
}

pg pg_dump "$CONN" --schema-only --schema=public --no-owner --no-privileges -f /out/schema.sql \
  || fail "schema export failed"

tables=$(pg psql "$CONN" -tA -c "select table_name from information_schema.tables
  where table_schema='public' and table_type='BASE TABLE' order by 1") || fail "couldn't list tables"
[[ -n "$tables" ]] || fail "no tables found"

# One transaction: every table and every count from the same moment.
{
  echo '\set ON_ERROR_STOP on'
  echo 'BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;'
  echo "\\o /out/manifest.txt"
  echo "select 'taken_at ' || to_char(now() at time zone 'utc', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"');"
  echo "select 'schema_version ' || max(version::int) from flyway_schema_history where success;"
  for t in $tables; do
    echo "select 'rows $t ' || count(*) from public.\"$t\";"
  done
  echo '\o'
  for t in $tables; do
    echo "\\copy public.\"$t\" to '/out/$t.csv' with (format csv, header)"
  done
  echo 'COMMIT;'
} > "$work/backup.sql"
pg psql "$CONN" -q -tA -f /out/backup.sql > /dev/null || fail "table export failed"

n=$(grep -c '^rows ' "$work/manifest.txt")
[[ "$n" -eq $(wc -w <<<"$tables") ]] || fail "manifest lists $n tables, expected $(wc -w <<<"$tables")"
rm "$work/backup.sql"

file="$OUT_DIR/prod-$stamp.tar.gz"
tar -czf "$file" -C "$work" . || fail "couldn't write $file"
log "OK $(basename "$file") $(du -h "$file" | cut -f1) $n tables, $(grep schema_version "$work/manifest.txt")"

# Rotation: drop backups older than KEEP_DAYS, but always keep the newest.
newest=$(ls -1t "$OUT_DIR"/prod-*.tar.gz 2>/dev/null | head -1)
find "$OUT_DIR" -maxdepth 1 -name 'prod-*.tar.gz' -mtime +"$KEEP_DAYS" ! -path "$newest" -print -delete \
  | while read -r old; do log "deleted old backup $(basename "$old")"; done
