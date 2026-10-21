# Backups and restoring

## What protects prod's data

1. **Neon's point-in-time restore** (the real backup). Neon keeps the
   database's history for the **restore window** set by the plan, and can
   restore it to any moment inside that window. `scripts/neon-restore-drill.bash`
   prints the current window.
   - **As of 2026-10-21 the window is only 6 hours.** Damage noticed later than
     that can't be undone from Neon; only our own CSV copies would help.
2. **Our own daily backup** (since 2026-10-21): `scripts/backup-prod.bash`
   writes `assets/data-backups/prod-<UTC time>.tar.gz` every day and keeps 14
   days. Each holds the schema, every table as CSV and a manifest, all from
   **one consistent snapshot**, taken as the read-only role. The folder is
   git-ignored and readable only by you: backups hold personal data.
3. **One-off CSV copies** taken by hand before risky changes, e.g.
   `backups/prod-before-V19-V35-20261020T040551Z/`.

## In an emergency: restore prod

Use the Neon console, not the drill script:

1. Neon console → the project → **Branches** → the production branch.
2. **Restore** → choose the time just *before* the damage → **Restore**.
3. Neon replaces the branch's data with that moment's and keeps a **backup
   branch** of what it replaced, so the restore itself can be undone.
4. Restart the backend so it reconnects:
   `flyctl machines restart -a pollsystem-backend` (one machine at a time is
   fine).
5. Check: `flyctl logs -a pollsystem-backend` (the startup log shows the schema
   version), and the site.

Anything written to prod after the chosen time is lost, though it's still in
the backup branch. For payments, Stripe keeps its own records; its webhook
events can be resent from the Stripe dashboard.

## The daily backup

- **Schedule:** on the host (not in the Dev Container), once:
  `./scripts/install-backup-timer.bash`. It sets up a systemd user timer:
  - runs daily at 03:30, or at the next start if the laptop was off;
  - takes the backup, then runs the restore check on it;
  - sends a desktop notification if either fails.
- **Check it:**
  - `systemctl --user list-timers pollsystem-backup` shows the next run;
  - `assets/data-backups/backup.log` has one line per backup;
  - `journalctl --user -u pollsystem-backup` has the details.
- **By hand:**
  - `./scripts/backup-prod.bash [--keep-days N]` takes a backup now;
  - `./scripts/restore-backup-check.bash [file]` loads a backup into a
    throwaway Postgres container and compares every table's row count with
    its manifest.
- **Restoring one for real** (when Neon's window has passed):
  1. Create an empty database, e.g. a new Neon branch with no data.
  2. Drop its empty `public` schema.
  3. Run `psql -f schema.sql`.
  4. Load each CSV with `session_replication_role = replica`.
  5. Set the sequences: `restore-backup-check.bash` shows each step.
  6. Point the backend at it, or copy the rows into prod.
- **A second copy, daily at 05:00:** `scripts/copy-backups.bash`.
  - It copies new backups to `nomad/local-backup/data-backups`, found under
    `/media/$USER`, `/run/media/$USER`, `/mnt`, `/media` or your home folder,
    or at a path you pass.
  - It checks each copy's checksum and keeps copies 90 days (`--keep-days`).
  - If the drive isn't there, it logs a failure and stops. It never recreates
    the mount point on the laptop's own disk.
  - Install once **on the host**: `./scripts/install-backup-copy-cron.bash
    [DEST]`. It runs a copy straight away to prove the path, then adds the
    cron line.
  - Cron doesn't catch up: if the laptop is off at 05:00, the next run copies
    everything missing.
  - Results go to `backup.log`.
- If `nomad` is a drive kept with the laptop, it doesn't protect against
  losing both. Now and then, also keep an encrypted copy somewhere else.

## The Neon drill

`./scripts/neon-restore-drill.bash [--at <UTC time>]` restores prod as it was at
that time (default: an hour ago) into a **temporary branch**. It then compares
the branch with live prod (schema version and row counts per table) and deletes
the branch. Prod is only read. It needs a Neon API key in
`.devcontainer/neon-api.secrets.env` (git-ignored).

Run it after any change to the Neon plan, and every few months.

### Drill log

| Date | Restored to | Result |
|---|---|---|
| 2026-10-21 07:03 UTC | 06:03 UTC (1 hour back) | ✅ Restored copy readable: schema 36 and identical row counts in all 32 tables. Branch deleted. **Restore window: only 6 hours.** |

### Daily backup check log

| Date | Backup | Result |
|---|---|---|
| 2026-10-21 | prod-20261021T071543Z (188 KB, 33 tables, schema 36) | ✅ Restored into a throwaway Postgres; all 33 tables' counts match. A tampered copy (one count changed) was correctly refused. |
