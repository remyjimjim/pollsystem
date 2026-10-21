#!/usr/bin/env bash
#
# copy-backups.bash — copy the daily prod backups (assets/data-backups) to a
# second place, so they survive losing the laptop's disk (decided 2026-10-21).
#
# Copies any backup not already there, checks each copy's checksum, and keeps
# the copies for --keep-days (default 90; the originals keep 14). Never deletes
# the newest copy. If the destination isn't there (an external drive not
# plugged in), it says so and stops, rather than filling the laptop's own disk
# at the mount point.
#
# Usage:
#   ./scripts/copy-backups.bash [DEST] [--keep-days N]
# DEST defaults to the first existing nomad/local-backup under /media/$USER,
# /run/media/$USER, /mnt, /media or $HOME, plus /data-backups.
# Runs daily at 05:00 via: ./scripts/install-backup-copy-cron.bash (host, once)

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/assets/data-backups"
KEEP_DAYS=90; DEST=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --keep-days) KEEP_DAYS="$2"; shift 2 ;;
    -h|--help) sed -n '2,19p' "$0"; exit 0 ;;
    *) DEST="$1"; shift ;;
  esac
done

log() { echo "$(date -u +%FT%TZ) copy: $*" | tee -a "$SRC/backup.log"; }
fail() { log "FAILED: $*"; exit 1; }

if [[ -z "$DEST" ]]; then
  for base in "/media/$USER" "/run/media/$USER" /mnt /media "$HOME"; do
    for d in "$base"/nomad/local-backup "$base"/*/nomad/local-backup; do
      [[ -d "$d" ]] && { DEST="$d/data-backups"; break 2; }
    done
  done
  [[ -n "$DEST" ]] || fail "no nomad/local-backup found (drive not plugged in?). Pass the destination path."
fi
# The folder holding DEST must already exist: a missing drive mount point must not be recreated on the laptop's disk.
[[ -d "$(dirname "$DEST")" ]] || fail "$(dirname "$DEST") isn't there (drive not plugged in?)"

umask 077
mkdir -p "$DEST" && chmod 700 "$DEST" || fail "can't create $DEST"

copied=0
for f in "$SRC"/prod-*.tar.gz; do
  [[ -e "$f" ]] || continue
  name=$(basename "$f"); target="$DEST/$name"
  [[ -f "$target" ]] && cmp -s "$f" "$target" && continue
  cp -p "$f" "$target.part" && mv "$target.part" "$target" || fail "copying $name"
  [[ "$(sha256sum < "$f")" == "$(sha256sum < "$target")" ]] || fail "checksum mismatch on $name"
  copied=$((copied + 1))
done

newest=$(ls -1t "$DEST"/prod-*.tar.gz 2>/dev/null | head -1)
removed=$(find "$DEST" -maxdepth 1 -name 'prod-*.tar.gz' -mtime +"$KEEP_DAYS" ! -path "$newest" -print -delete | wc -l)
log "OK to $DEST: $copied new, $(ls -1 "$DEST"/prod-*.tar.gz 2>/dev/null | wc -l) kept, $removed older than $KEEP_DAYS days removed"
