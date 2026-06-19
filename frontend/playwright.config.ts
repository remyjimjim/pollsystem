import { defineConfig, devices } from '@playwright/test'

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
