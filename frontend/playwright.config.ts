import { defineConfig, devices } from '@playwright/test'

// Let specs read a `state=<name>` CLI token, e.g.
//   npx playwright test register-colorado-users state=texas
// Playwright runs specs in worker processes that do NOT inherit the CLI
// positionals, but they DO inherit env vars set here in the main process —
// so we translate the token into E2E_STATE for the spec to read. (Playwright
// also treats the token as a filename filter, which matches no file and so
// doesn't change which specs run.)
const stateArg = process.argv.find((a) => /^state=/i.test(a))
if (stateArg) process.env.E2E_STATE = stateArg.slice(stateArg.indexOf('=') + 1)

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
