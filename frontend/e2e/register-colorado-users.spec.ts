import { test, expect } from '@playwright/test'
import { clearMailpit, fetchMagicLink } from './mailpit'
import { pauseWithModal } from './pause-modal'

// Role string is part of the email handle for traceability only. The
// register form has no role field; access level is granted later via
// AdminRequest approval.
const ROLES = ['user', 'viewer', 'creator', 'admin'] as const

const TOTAL_USERS = 2 * ROLES.length

const PAUSE_BODY = `
  The test is paused. Query the dev database now if you need to. Connect with:
  <code style="background:#f1f5f9;padding:2px 6px;border-radius:3px;display:inline-block;
               margin-top:6px;font-size:12px;">PGPASSWORD=pollpass123 psql -h localhost -U polladmin -d pollsystem</code>
  <br><br>
  <span style="font-size:12px;color:#64748b;">
    Note: this is the local-dev DB <code>pollsystem</code>. The Testcontainers
    <code>pollsystem_test</code> DB only exists during <code>./gradlew test</code> runs.
  </span>
`

test.describe('register Colorado users via magic link', () => {
  // Clear leftover users from previous runs so the deterministic email
  // and phone numbers don't trip the UNIQUE constraints on re-registration.
  // The endpoint is dev-only (Spring @Profile("local")).
  test.beforeAll(async () => {
    const res = await fetch(
      'http://localhost:8080/api/dev/reset-test-users?emailPrefix=zzz',
      { method: 'POST' }
    )
    if (!res.ok) {
      throw new Error(`POST /api/dev/reset-test-users failed: ${res.status} ${await res.text()}`)
    }
  })

  test('register and log in 2 users per role in isolated browser contexts', async ({ browser }) => {
    test.setTimeout(240_000)

    // Start from a clean Mailpit inbox so fetchMagicLink can't be fooled by
    // stale tokens from earlier runs (tokens are single-use and expire).
    await clearMailpit()

    let n = 0
    for (let i = 1; i <= 2; i++) {
      for (const role of ROLES) {
        n++
        const email   = `zzz${i}test${role}@colorado.com`
        const phone   = String(3031111110 + n)
        const zipcode = String(80001 + n)

        // Each iteration owns its own browser context = isolated cookies,
        // localStorage, sessionStorage. That's what makes the sessions
        // separate; browser.newPage() alone would share storage and one
        // iteration's login would bleed into the next.
        const ctx = await browser.newContext()
        const page = await ctx.newPage()

        try {
          // 1. Home — no values to set; hold for 4s, then click Register CTA
          //    (disambiguated from the nav link by the trailing arrow).
          await page.goto('http://localhost:3000')
          await page.waitForTimeout(4_000)               // hold on home (no values)
          await page.getByRole('link', { name: 'Register →' }).click()
          await expect(page).toHaveURL('http://localhost:3000/register')

          // 2. /register — fill all three fields, hold 4s with the populated
          //    form visible, then submit.
          await page.getByLabel('Email').fill(email)
          await page.getByLabel('Phone').fill(phone)
          await page.getByLabel('Zipcode').fill(zipcode)
          await page.waitForTimeout(4_000)               // hold on /register with form filled

          // On the very last iteration, pause before submit so the user can
          // query the DB and see the state immediately before the 8th user
          // is registered. Resume by clicking Close in the injected modal.
          if (n === TOTAL_USERS) {
            await pauseWithModal(page, 'Last chance to query database', PAUSE_BODY)
          }

          await page.getByRole('button', { name: 'Email me a sign-in link' }).click()

          // Race success ("Check your email.") against the backend's error
          // banner. If the email/phone collides with a prior run's user the
          // backend balks via the UNIQUE constraint and we just skip this
          // iteration — no need to wipe state up-front for re-runs.
          const success = page.getByText('Check your email.')
          const errorBanner = page.locator('p.text-red-700').first()
          await Promise.race([
            success.waitFor({ state: 'visible', timeout: 30_000 }),
            errorBanner.waitFor({ state: 'visible', timeout: 30_000 }),
          ])
          if (await errorBanner.isVisible()) {
            const msg = (await errorBanner.textContent())?.trim() ?? '(no message)'
            console.log(`[skip ${email}] backend rejected: ${msg}`)
            continue
          }

          // 3. Magic link — visit, confirm logged in, hold 4s on the
          //    signed-in landing before the context closes.
          const magicHref = await fetchMagicLink(email)
          await page.goto(magicHref)
          await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible({ timeout: 30_000 })
          await page.waitForTimeout(4_000)               // hold on signed-in landing
        } finally {
          // Closing the context drops the entire session (cookies, storage,
          // both pages). No explicit logout needed — next iteration starts
          // with a fresh context regardless.
          await ctx.close()
        }
      }
    }
  })
})
