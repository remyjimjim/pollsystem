import { defineConfig, devices } from '@playwright/test'

// Let specs read `key=value` CLI tokens, e.g.
//   npx playwright test user-registers-submits-poll state=iowa county=Polk keep=yes
// Playwright runs specs in worker processes that do NOT inherit the CLI
// positionals, but they DO inherit env vars set here in the main process — so we
// translate known tokens into env vars for the specs (and global-teardown) to
// read. (Playwright also treats each token as a filename filter, which matches
// no file and so doesn't change which specs run.) A bare token (e.g. `keep`)
// means "yes".
const CLI_TOKENS: Record<string, string> = {
  state: 'E2E_STATE',
  county: 'E2E_COUNTY',
  keep: 'E2E_KEEP',
  wipe: 'E2E_WIPE',
  hold: 'E2E_HOLD_MS', // watchable-pause length in ms for --headed runs (0 = off)
}
for (const arg of process.argv.slice(2)) {
  const eq = arg.indexOf('=')
  const key = (eq === -1 ? arg : arg.slice(0, eq)).toLowerCase()
  const envName = CLI_TOKENS[key]
  if (envName) process.env[envName] = eq === -1 ? 'yes' : arg.slice(eq + 1)
}

// Scoping testDir to ./e2e keeps Playwright from picking up the *.spec.ts
// files under src/ that belong to Vitest.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false, // the suite hits a shared backend + Mailpit
  reporter: 'list',
  // Runs once after every spec file finishes. Wipes the zzz-prefixed test
  // users + everything anchored to them. Skip with SKIP_TEARDOWN=1.
  // Lives outside testDir so Playwright's spec discovery doesn't try to
  // load it twice (once as teardown, once as a candidate spec file).
  globalTeardown: './playwright/global-teardown.ts',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
})
