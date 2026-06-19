// Runs once after the entire `playwright test` invocation, regardless of
// pass/fail counts or how many spec files ran.
//
// Calls /api/dev/reset-test-users on the local backend to drop every
// zzz-prefixed user and the data anchored to them (magic_link_tokens,
// role_assignments, plus the long tail of FK paths to users(id)).
//
// Override the prefix with E2E_USER_PREFIX, or skip the wipe entirely
// with SKIP_TEARDOWN=1 — useful when a paused test (e.g. the iteration-8
// modal) leaves state behind that you still want to inspect after the
// run finishes.

export default async function globalTeardown() {
  if (process.env.SKIP_TEARDOWN === '1') {
    console.log('globalTeardown: SKIP_TEARDOWN=1 — leaving test data in place')
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
