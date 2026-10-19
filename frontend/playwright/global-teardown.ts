// Runs once after the entire `playwright test` invocation, regardless of
// pass/fail counts or how many spec files ran.
//
// By default this LEAVES the seeded (zzz-prefixed) users in place, so they
// persist for the next script and for manual inspection — cleanliness is
// owned by each "registers" script's beforeAll pre-wipe. Pass `wipe=yes` (or
// WIPE=1 / SKIP_TEARDOWN unset is irrelevant now) to opt in to a post-run
// wipe: /api/dev/reset-test-users drops every zzz user and the data anchored
// to them (magic_link_tokens, role_assignments, plus the long tail of FK
// paths to users(id)). Override the prefix with E2E_USER_PREFIX.
//
// `wipe=yes` is parsed into E2E_WIPE by playwright.config.ts (this teardown
// runs in the main process, so it also inherits that env var).

function isTruthy(v: string | undefined): boolean {
  return v != null && ['yes', '1', 'true', 'on'].includes(v.toLowerCase())
}

// Put back the results threshold global-setup.ts set aside (Dev settings).
async function restoreResultsThreshold() {
  const saved = process.env.E2E_SAVED_RESULTS_THRESHOLD
  if (!saved) return
  try {
    await fetch('http://localhost:8080/api/dev/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ resultsThreshold: Number(saved) }),
    })
    console.log(`globalTeardown: results threshold restored to ${saved}`)
  } catch (err) {
    console.warn(`globalTeardown: couldn't restore the results threshold to ${saved} (${(err as Error).message})`)
  }
}

export default async function globalTeardown() {
  await restoreResultsThreshold()
  if (!isTruthy(process.env.E2E_WIPE)) {
    console.log('globalTeardown: leaving test data in place (pass wipe=yes to clear it)')
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
