# Backups and restoring

## What protects prod's data

1. **Neon's point-in-time restore** (the real backup). Neon keeps the
   database's history for the **restore window** set by the plan, and can
   restore it to any moment inside that window. `scripts/neon-restore-drill.bash`
   prints the current window.
   - **As of 2026-10-21 the window is only 6 hours.** Damage noticed later than
     that can't be undone from Neon; only our own CSV copies would help.
2. **CSV copies we take ourselves** before risky changes, e.g.
   `backups/prod-before-V19-V35-20261020T040551Z/`. They're git-ignored and
   readable only by you, because they hold personal data.
   (`pg_dump` can't run as the read-only role, so it's one CSV per table.)

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

## The drill

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
