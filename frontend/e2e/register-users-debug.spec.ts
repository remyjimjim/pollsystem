import { test, expect } from '@playwright/test'

// Parameterized debug variant of register-colorado-users.spec.ts: opens N
// browser contexts (one per user), keeps every window open at the end, and
// hands control to the Playwright Inspector so you can poke around the
// signed-in sessions before the test tears everything down.
//
// Run with:
//   npx playwright test register-users-debug --headed --debug
//
// Without --debug, page.pause() will hang the test until the suite times
// out — that's by design (page.pause is a no-op gate in headless mode).
//
// Add scenarios to SCENARIOS to fan out further (e.g. a second state). Each
// entry produces its own test() case.

type Scenario = {
  name: string
  userCount: number
  emailFor: (i: number) => string
  phoneFor: (i: number) => string
  zipFor: (i: number) => string
}

const SCENARIOS: Scenario[] = [
  {
    name: 'colorado',
    userCount: 10,
    emailFor: (i) => `zzz${i}testuser@colorado.com`,
    phoneFor: (i) => String(3031111110 + i),
    zipFor:   (i) => String(80001 + i),
  },
  // {
  //   name: 'california',
  //   userCount: 5,
  //   emailFor: (i) => `zzz${i}testuser@california.com`,
  //   phoneFor: (i) => String(2131111110 + i),
  //   zipFor:   (i) => String(90001 + i),
  // },
]

for (const scenario of SCENARIOS) {
  test(`debug: register ${scenario.userCount} ${scenario.name} users; keep windows open until Resume`, async ({ browser }) => {
    // Long timeout — the test waits on a human at the end.
    test.setTimeout(30 * 60_000)

    const openPages: import('@playwright/test').Page[] = []

    for (let i = 1; i <= scenario.userCount; i++) {
      const email   = scenario.emailFor(i)
      const phone   = scenario.phoneFor(i)
      const zipcode = scenario.zipFor(i)

      // Fresh context per user → isolated cookies / localStorage.
      const ctx = await browser.newContext()
      const page = await ctx.newPage()

      // Home → Register
      await page.goto('http://localhost:3000')
      await page.getByRole('link', { name: 'Register' }).click()

      // Fill form and submit
      await page.getByLabel('Email').fill(email)
      await page.getByLabel('Phone').fill(phone)
      await page.getByLabel('Zipcode').fill(zipcode)
      await page.getByRole('button', { name: 'Email me a sign-in link' }).click()
      await expect(page.getByText('Check your email.')).toBeVisible()

      // Mailpit → this user's email → magic link
      const mail = await ctx.newPage()
      await mail.goto('http://localhost:8025')
      await mail.locator('.message-list .message', { hasText: email }).first().click()
      const magicHref = await mail.frameLocator('iframe')
        .locator('a[href^="http://localhost:3000/auth/magic-link?token="]')
        .first()
        .getAttribute('href')
      expect(magicHref, `no magic link found for ${email}`).not.toBeNull()
      await mail.close() // drop just the Mailpit tab; keep the main page open

      // Sign in via the magic link; leave the page open for inspection
      await page.goto(magicHref!)
      await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible()

      openPages.push(page)
    }

    console.log(
      `\nAll ${openPages.length} ${scenario.name} users registered and signed in. ` +
      `Inspect the windows, then click Resume in the Playwright Inspector to ` +
      `finish (and let Playwright close everything).\n`
    )

    // Gate the teardown on a human Resume click. Only meaningful under
    // --headed --debug; otherwise it hangs until the test timeout.
    await openPages[0].pause()
  })
}
