#!/usr/bin/env bash
# Stop-hook reminder for the development log.
#
# Warns when the newest substantive commit (one that changed something
# other than docs/DEVLOG.md itself) has no corresponding DEVLOG entry —
# i.e. neither that commit nor any commit after it touched docs/DEVLOG.md.
#
# Exit 0  = nothing to flag.
# Exit 2  = surface the reminder to Claude so the entry gets added.

root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
cd "$root" || exit 0

# Newest commit that touched a file other than the DEVLOG itself.
last=$(git log -1 --format='%H' -- . ':(exclude)docs/DEVLOG.md' 2>/dev/null)
[ -z "$last" ] && exit 0

# Logged if that commit itself touched the DEVLOG, or any later commit did.
if git show --name-only --format= "$last" 2>/dev/null | grep -qx 'docs/DEVLOG.md' \
   || [ -n "$(git log "${last}..HEAD" --format='%H' -- docs/DEVLOG.md 2>/dev/null)" ]; then
  exit 0
fi

short=$(git rev-parse --short "$last")
subject=$(git log -1 --format='%s' "$last")
echo "DEVLOG reminder: commit ${short} (\"${subject}\") has no docs/DEVLOG.md entry yet. Add one per the Entry format section of docs/DEVLOG.md before finishing." >&2
exit 2
