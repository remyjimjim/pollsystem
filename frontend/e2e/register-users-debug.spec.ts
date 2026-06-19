import { test, expect } from '@playwright/test'
import { clearMailpit, fetchMagicLink } from './mailpit'
import { pauseWithModal } from './pause-modal'

// Parameterized debug variant of register-colorado-users.spec.ts: opens N
// browser contexts (one per user), keeps every window open at the end, and
// shows an HTML modal with a Close button to gate teardown. Run with:
//
//   npx playwright test register-users-debug --headed
//
// No --debug needed — pauseWithModal injects its own DOM overlay rather
// than relying on Playwright's Inspector. Close the modal in any one of
// the open windows to let the test finish (and let globalTeardown wipe
// the registered users).
//
// Add scenarios to SCENARIOS to fan out further (e.g. a second state). Each
// entry produces its own test() case.

type Scenario = {
  name: string
  iterations: number
  roles: readonly string[]
  emailFor: (i: number, role: string) => string
  phoneFor: (n: number) => string
  zipFor: (n: number) => string
}

const SCENARIOS: Scenario[] = [
  {
    name: 'colorado',
    iterations: 2,
    roles: ['user', 'viewer', 'creator', 'admin'],
    emailFor: (i, role) => `zzz${i}test${role}@colorado.com`,
    phoneFor: (n) => String(3031111110 + n),
    zipFor:   (n) => String(80001 + n),
  },
  // {
  //   name: 'california',
  //   iterations: 1,
  //   roles: ['user', 'viewer', 'creator', 'admin'],
  //   emailFor: (i, role) => `zzz${i}test${role}@california.com`,
  //   phoneFor: (n) => String(2131111110 + n),
  //   zipFor:   (n) => String(90001 + n),
  // },
]

for (const scenario of SCENARIOS) {
  const total = scenario.iterations * scenario.roles.length
  test(`debug: register ${total} ${scenario.name} users (${scenario.iterations} per role); keep windows open until Close`, async ({ browser }) => {
    // Long timeout — the test waits on a human at the end.
    test.setTimeout(30 * 60_000)

    // Clear leftover users so deterministic phones/emails don't collide
    // with state from a prior run that didn't get cleaned up.
    const resetRes = await fetch(
      'http://localhost:8080/api/dev/reset-test-users?emailPrefix=zzz',
      { method: 'POST' }
    )
    if (!resetRes.ok) {
      throw new Error(`POST /api/dev/reset-test-users failed: ${resetRes.status}`)
    }

    await clearMailpit()

    const openPages: import('@playwright/test').Page[] = []

    let n = 0
    for (let i = 1; i <= scenario.iterations; i++) {
      for (const role of scenario.roles) {
        n++
        const email   = scenario.emailFor(i, role)
        const phone   = scenario.phoneFor(n)
        const zipcode = scenario.zipFor(n)

        // Fresh context per user → isolated cookies / localStorage.
        const ctx = await browser.newContext()
        const page = await ctx.newPage()

        // Home → Register CTA (disambiguated from the nav link by the trailing arrow)
        await page.goto('http://localhost:3000')
        await page.getByRole('link', { name: 'Register →' }).click()

        // Fill form and submit
        await page.getByLabel('Email').fill(email)
        await page.getByLabel('Phone').fill(phone)
        await page.getByLabel('Zipcode').fill(zipcode)
        await page.getByRole('button', { name: 'Email me a sign-in link' }).click()
        // Mailpit SMTP send can take >5s; bump the assertion wait.
        await expect(page.getByText('Check your email.')).toBeVisible({ timeout: 30_000 })

        // Pull the magic link from Mailpit's API and visit it.
        const magicHref = await fetchMagicLink(email)
        await page.goto(magicHref)
        await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible({ timeout: 30_000 })

        openPages.push(page)
      }
    }

    console.log(
      `\nAll ${openPages.length} ${scenario.name} users registered and signed in. ` +
      `Click Close on the modal to finish (and let Playwright + globalTeardown ` +
      `clean everything up).\n`
    )

    // Gate teardown on a human Close click via an injected DOM overlay —
    // works under plain --headed, no --debug / Inspector required.
    await pauseWithModal(
      openPages[0],
      `All ${openPages.length} ${scenario.name} users registered`,
      'Inspect any of the open windows. Click Close to finish the test and let globalTeardown wipe the registered users.'
    )
  })
}
