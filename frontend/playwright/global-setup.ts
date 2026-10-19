// Runs once before the whole `playwright test` run.
//
// The specs assume the default results privacy threshold (10: results hidden
// until 10 respondents). The local Dev settings page can change it, so if it's
// changed, note it, use the default for this run, and let global-teardown.ts
// put it back (setup and teardown share this process, hence the env var).
// A backend without the dev endpoints (not local) is left alone.

const SETTINGS = 'http://localhost:8080/api/dev/settings'

export default async function globalSetup() {
  try {
    const res = await fetch(SETTINGS)
    if (!res.ok) return
    const s = await res.json() as { resultsThreshold: number; resultsThresholdOverridden: boolean }
    if (!s.resultsThresholdOverridden) return
    process.env.E2E_SAVED_RESULTS_THRESHOLD = String(s.resultsThreshold)
    await fetch(SETTINGS, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ resultsThreshold: null }),
    })
    console.log(`globalSetup: results threshold was ${s.resultsThreshold} (Dev settings); using the default for this run, restored afterwards`)
  } catch {
    // Backend unreachable: the specs will say so themselves.
  }
}
