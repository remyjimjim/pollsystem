#!/usr/bin/env bash
#
# restore-backup-check.bash — prove a backup from backup-prod.bash restores.
#
# Loads the backup into a THROWAWAY Postgres 18 container (never prod, never
# the local dev database): the schema, then every table's rows, then sets each
# id sequence past the highest id. Compares every table's row count with the
# backup's manifest, then removes the container.
#
# Usage:
#   ./scripts/restore-backup-check.bash                     # the newest backup
#   ./scripts/restore-backup-check.bash assets/data-backups/prod-….tar.gz
#
# To restore a backup for real, the same steps work against an empty database
# (a new Neon branch, for instance): drop the empty schema public, psql -f schema.sql, then the \copy lines
# below with session_replication_role = replica, then the sequence fix.

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
file="${1:-$(ls -1t "$ROOT"/assets/data-backups/prod-*.tar.gz 2>/dev/null | head -1)}"
[[ -n "$file" && -f "$file" ]] || { echo "no backup found" >&2; exit 1; }
name="restore-check-$$"

umask 077
# Under the project: the Docker daemon may run on the host, which sees this path but not our /tmp.
work=$(mktemp -d "$ROOT/assets/data-backups/.restore-XXXX")
cleanup() { docker rm -f "$name" >/dev/null 2>&1; rm -rf "$work"; }
trap cleanup EXIT
tar -xzf "$file" -C "$work"
chmod 755 "$work"; chmod 644 "$work"/*        # readable by the container's postgres user

echo "Backup: $(basename "$file") ($(grep taken_at "$work/manifest.txt"), $(grep schema_version "$work/manifest.txt"))"
docker run -d --name "$name" -e POSTGRES_PASSWORD=x -v "$work:/backup:ro" postgres:18 >/dev/null
for i in $(seq 1 30); do docker exec "$name" pg_isready -U postgres -q && break; sleep 1; done
q() { docker exec -i "$name" psql -U postgres -v ON_ERROR_STOP=1 -q "$@"; }

# The dump creates schema public itself; a fresh database already has an empty one.
q -c 'DROP SCHEMA public CASCADE' >/dev/null
q -f /backup/schema.sql >/dev/null || { echo "✗ schema failed to load"; exit 1; }
{
  echo 'SET session_replication_role = replica;'          # rows in any order: skip FK checks while loading
  for csv in "$work"/*.csv; do t=$(basename "$csv" .csv); echo "\\copy public.\"$t\" from '/backup/$t.csv' with (format csv, header)"; done
  # Each id sequence continues after the highest restored id.
  echo "select setval(s.seq, coalesce(max_id, 0) + 1, false) from (
          select pg_get_serial_sequence(format('public.%I', c.table_name), c.column_name) seq, c.table_name, c.column_name
          from information_schema.columns c where c.table_schema = 'public'
            and pg_get_serial_sequence(format('public.%I', c.table_name), c.column_name) is not null) s,
        lateral (select (xpath('/row/m/text()', query_to_xml(format('select max(%I) as m from public.%I', s.column_name, s.table_name), false, true, '')))[1]::text::bigint max_id) m;"
} | q >/dev/null || { echo "✗ data failed to load"; exit 1; }

bad=0; n=0
while read -r _ t want; do
  n=$((n+1))
  got=$(q -tA -c "select count(*) from public.\"$t\"" < /dev/null)   # don't let docker eat the manifest
  [[ "$got" == "$want" ]] || { echo "✗ $t: $got rows restored, backup says $want"; bad=1; }
done < <(grep '^rows ' "$work/manifest.txt")
if (( bad )); then echo "✗ restore check FAILED"; exit 1; fi
echo "✓ restored into a throwaway database: all $n tables match the backup's row counts"
