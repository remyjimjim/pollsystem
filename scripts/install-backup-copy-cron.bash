#!/usr/bin/env bash
#
# install-backup-copy-cron.bash — run copy-backups.bash daily at 05:00, via
# your crontab. Run it ON THE HOST (not inside the Dev Container), once.
#
# Usage:
#   ./scripts/install-backup-copy-cron.bash [DEST]   # install / update
#   ./scripts/install-backup-copy-cron.bash --uninstall
#   crontab -l                                       # see it
#
# Cron doesn't catch up: if the laptop is off or asleep at 05:00, that day's
# copy is skipped, and the next one copies everything missing. Results go to
# assets/data-backups/backup.log.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TAG="# pollsystem: copy prod backups"

if [[ -f /.dockerenv || -n "${REMOTE_CONTAINERS:-}" ]]; then
  echo "Run this on the host, not inside the Dev Container." >&2; exit 1
fi
command -v crontab >/dev/null || { echo "crontab isn't installed (sudo apt install cron)" >&2; exit 1; }

current=$(crontab -l 2>/dev/null | grep -vF "$TAG" || true)
if [[ "${1:-}" == "--uninstall" ]]; then
  printf '%s\n' "$current" | crontab -
  echo "Removed the 05:00 backup copy."; exit 0
fi

dest="${1:-}"
# Try it now, so a wrong or missing destination shows up today, not at 05:00.
"$ROOT/scripts/copy-backups.bash" ${dest:+"$dest"}

line="0 5 * * * \"$ROOT/scripts/copy-backups.bash\"${dest:+ \"$dest\"} >/dev/null 2>&1 $TAG"
printf '%s\n%s\n' "$current" "$line" | sed '/^$/d' | crontab -
echo "Installed: daily at 05:00"
crontab -l | grep -F "$TAG"
