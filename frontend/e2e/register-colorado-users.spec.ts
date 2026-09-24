/// <reference types="node" />
import { test, expect, type Page } from '@playwright/test'
import { clearMailpit, fetchMagicLink } from './mailpit'
import { pauseWithModal } from './pause-modal'

// The per-screen pauses and the pre-final-user modal below exist only to make a
// live, headed run watchable (hold on each screen; stop to inspect the DB). In
// CI they're useless and the modal would hang forever, so skip them there.
// GitHub Actions (and most CI) set CI=true.
const INTERACTIVE = !process.env.CI
async function hold(page: Page, ms = 4_000): Promise<void> {
  if (INTERACTIVE) await page.waitForTimeout(ms)
}

// Role string is part of the email handle for traceability only. The
// register form has no role field; access level is granted later via
// AdminRequest approval.
const ROLES = ['viewer', 'user', 'creator', 'admin'] as const

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

  // One continuous browser session: register a user, sign in via the magic
  // link, then Logout before the next user — matching the manual flow. A
  // single page (not a context-per-user) is what makes the explicit Logout
  // meaningful; the Logout is what separates the sessions.
  test('register and log in 8 Colorado users, logging out between each', async ({ page }) => {
    test.setTimeout(240_000)

    // Start from a clean Mailpit inbox so fetchMagicLink can't be fooled by
    // stale tokens from earlier runs (tokens are single-use and expire).
    await clearMailpit()

    let n = 0
    for (let i = 1; i <= 2; i++) {
      for (const role of ROLES) {
        n++
        const email = `zzz${i}test${role}@colorado.com`
        // Colorado 303-534 numbers, one per user so each clears the UNIQUE
        // phone constraint (a per-i number would collide across the 4 roles).
        // 361 is another plausible exchange if more ranges are ever needed.
        const phone   = String(3035341110 + n)   // 3035341111 … 3035341118
        const zipcode = '80202'                   // Denver; zipcode isn't unique-constrained

        // 1. Home — no values to set; hold for 4s, then click the Register CTA
        //    under the "Create an account" card (the trailing arrow
        //    disambiguates it from the nav "Register" link).
        await page.goto('http://localhost:3000')
        await hold(page)                               // hold on home (no values)
        await page.getByRole('link', { name: 'Register →' }).click()
        await expect(page).toHaveURL('http://localhost:3000/register')

        // 2. /register — fill all three fields, hold 4s with the populated
        //    form visible, then submit.
        await page.getByLabel('Email').fill(email)
        await page.getByLabel('Phone').fill(phone)
        await page.getByLabel('Zipcode').fill(zipcode)
        await hold(page)                               // hold on /register with form filled

        // On the very last iteration, pause before submit so the user can
        // query the DB and see the state immediately before the 8th user
        // is registered. Resume by clicking Close in the injected modal.
        // Interactive-only — skipped in CI (it would block until timeout).
        if (n === TOTAL_USERS && INTERACTIVE) {
          await pauseWithModal(page, 'Last chance to query database', PAUSE_BODY)
        }

        await page.getByRole('button', { name: /Continue to payment/ }).click()

        // Success (mock or real Stripe) redirects back to the home page as
        // ?checkout=success, which renders the green "Payment received…"
        // banner. A dupe email/phone trips the UNIQUE constraint and the
        // backend keeps us on /register with a red error banner — race the
        // two and skip the user on error (no need to wipe state for re-runs).
        const errorBanner = page.locator('p.text-red-700').first()
        const outcome = await Promise.race([
          page.waitForURL(/checkout=success/, { timeout: 30_000 })
            .then(() => 'ok' as const).catch(() => 'timeout' as const),
          errorBanner.waitFor({ state: 'visible', timeout: 30_000 })
            .then(() => 'error' as const).catch(() => 'timeout' as const),
        ])
        if (outcome === 'error') {
          const msg = (await errorBanner.textContent())?.trim() ?? '(no message)'
          console.log(`[skip ${email}] backend rejected: ${msg}`)
          continue   // no session was established; still logged out for the next user
        }
        await expect(page.getByText(/Payment received/)).toBeVisible({ timeout: 10_000 })

        // 3. Magic link — pulled from Mailpit's REST API (more reliable than
        //    scraping its UI iframe), then visited to sign in. Confirm we're
        //    logged in, and hold on the signed-in landing.
        const magicHref = await fetchMagicLink(email)
        await page.goto(magicHref)
        await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible({ timeout: 30_000 })
        await hold(page)                               // hold on signed-in landing

        // 4. Logout — drop this user's session before registering the next.
        await page.getByRole('button', { name: 'Logout' }).click()
        await expect(page.getByRole('button', { name: 'Logout' })).toBeHidden({ timeout: 10_000 })
      }
    }
  })
})
