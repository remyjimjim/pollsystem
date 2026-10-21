#!/usr/bin/env bash
#
# install-backup-timer.bash — run backup-prod.bash every day, on this machine.
#
# Run it ON THE HOST (not inside the Dev Container), once. It installs a
# systemd user timer:
#   - daily at 03:30 local time; if the machine was off or asleep then, it
#     runs at the next start (Persistent=true);
#   - each run takes a backup, then proves it restores (restore-backup-check);
#   - if either fails, a desktop notification says so; details in
#     `journalctl --user -u pollsystem-backup` and assets/data-backups/backup.log.
#
# Usage:
#   ./scripts/install-backup-timer.bash              # install / update, and enable
#   ./scripts/install-backup-timer.bash --uninstall
#   systemctl --user list-timers pollsystem-backup   # when it runs next
#   systemctl --user start pollsystem-backup         # run one now
#
# To have it run even while you're logged out: loginctl enable-linger "$USER"

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UNITS="$HOME/.config/systemd/user"

if [[ -f /.dockerenv || -n "${REMOTE_CONTAINERS:-}" ]]; then
  echo "Run this on the host, not inside the Dev Container." >&2; exit 1
fi

if [[ "${1:-}" == "--uninstall" ]]; then
  systemctl --user disable --now pollsystem-backup.timer 2>/dev/null || true
  rm -f "$UNITS"/pollsystem-backup{,-failed}.service "$UNITS"/pollsystem-backup.timer
  systemctl --user daemon-reload
  echo "Removed the daily backup timer. Existing backups are untouched."; exit 0
fi

command -v docker >/dev/null || { echo "docker isn't on PATH here" >&2; exit 1; }
mkdir -p "$UNITS"

cat > "$UNITS/pollsystem-backup.service" <<UNIT
[Unit]
Description=Pollsystem: daily prod database backup, then a restore check
After=network-online.target docker.service
OnFailure=pollsystem-backup-failed.service

[Service]
Type=oneshot
WorkingDirectory=$ROOT
ExecStart=$ROOT/scripts/backup-prod.bash
ExecStart=$ROOT/scripts/restore-backup-check.bash
UNIT

cat > "$UNITS/pollsystem-backup-failed.service" <<UNIT
[Unit]
Description=Pollsystem: tell me the backup failed

[Service]
Type=oneshot
ExecStart=-/usr/bin/notify-send -u critical "Pollsystem backup FAILED" "See: journalctl --user -u pollsystem-backup"
UNIT

cat > "$UNITS/pollsystem-backup.timer" <<UNIT
[Unit]
Description=Pollsystem: daily prod database backup

[Timer]
OnCalendar=*-*-* 03:30
Persistent=true
RandomizedDelaySec=10m

[Install]
WantedBy=timers.target
UNIT

systemctl --user daemon-reload
systemctl --user enable --now pollsystem-backup.timer
echo "Installed. Next run:"
systemctl --user list-timers pollsystem-backup.timer --no-pager | sed -n 1,2p
