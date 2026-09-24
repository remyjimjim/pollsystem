// Runs once after the entire `playwright test` invocation, regardless of
// pass/fail counts or how many spec files ran.
//
// Calls /api/dev/reset-test-users on the local backend to drop every
// zzz-prefixed user and the data anchored to them (magic_link_tokens,
// role_assignments, plus the long tail of FK paths to users(id)).
//
// Override the prefix with E2E_USER_PREFIX. Skip the wipe entirely — to
// keep the seeded users in the DB (e.g. so the per-role e2e scripts can run
// against them, or a paused test left state you want to inspect) — either
// with SKIP_TEARDOWN=1 or by passing a `keep` token on the command line,
// e.g. `npx playwright test register-colorado-users keep=yes --headed`.
// (Playwright reads unknown positional args as filename filters, so an
// extra `keep=yes` doesn't change which specs run; we just look for it in
// process.argv here.)

// Truthy forms: bare `keep`, or keep=yes|1|true|on (case-insensitive).
function keepRequested(): boolean {
  if (process.env.SKIP_TEARDOWN === '1') return true
  for (const arg of process.argv.slice(2)) {
    const m = /^keep(?:=(.*))?$/i.exec(arg)
    if (m) {
      const val = (m[1] ?? '').toLowerCase()
      return val === '' || ['yes', '1', 'true', 'on'].includes(val)
    }
  }
  return false
}

export default async function globalTeardown() {
  if (keepRequested()) {
    console.log('globalTeardown: keep flag set — leaving test data in place')
    return
  }

  const prefix = process.env.E2E_USER_PREFIX ?? 'zzz'
  const url = `http://localhost:8080/api/dev/reset-test-users?emailPrefix=${encodeURIComponent(prefix)}`

  try {
    const res = await fetch(url, { method: 'POST' })
    if (!res.ok) {
      console.warn(`globalTeardown: ${url} returned ${res.status} ${await res.text().catch(() => '')}`)
      return
    }
    const body = await res.json() as { deleted: number; rowsByTable?: Record<string, number> }
    console.log(`globalTeardown: removed ${body.deleted} user(s) with prefix '${prefix}'`)
    if (body.rowsByTable) {
      const nonzero = Object.entries(body.rowsByTable).filter(([, n]) => n > 0)
      if (nonzero.length > 0) {
        console.log(`  cascaded: ${nonzero.map(([t, n]) => `${t}=${n}`).join(', ')}`)
      }
    }
  } catch (err) {
    // Don't fail the run on teardown problems — the user already has
    // their result. Just surface the reason so it isn't silent.
    console.warn(`globalTeardown: ${url} unreachable (${(err as Error).message})`)
  }
}
