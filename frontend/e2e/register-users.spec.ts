/// <reference types="node" />
import { test, expect, type Page } from '@playwright/test'
import { clearMailpit, fetchMagicLink } from './mailpit'
import { pauseWithModal } from './pause-modal'
import { KEEP, STATE_INPUT, resolveLocation, resetTestUsers, seededEmail } from './seed'

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

// Which state to register users in. Pass `state=<name>` (full name or 2-letter
// initial; default colorado, optionally narrowed by county=<name>) — parsed
// into E2E_STATE by playwright.config.ts. The geography lookup and the
// seeded-email convention live in ./seed.
const STATE = STATE_INPUT.toLowerCase()

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

test.describe(`register ${STATE} users via magic link`, () => {
  // This script *registers* users, so it pre-wipes leftover zzz users from
  // previous runs — the deterministic email/phone would otherwise trip the
  // UNIQUE constraints. keep=yes skips the pre-wipe to layer onto existing
  // data. (The global teardown leaves data in place; pass wipe=yes to clear.)
  test.beforeAll(async () => {
    if (KEEP) {
      console.log('[keep] skipping pre-wipe of zzz users')
      return
    }
    await resetTestUsers('zzz')
  })

  // One continuous browser session: register a user, sign in via the magic
  // link, then Logout before the next user — matching the manual flow. A
  // single page (not a context-per-user) is what makes the explicit Logout
  // meaningful; the Logout is what separates the sessions.
  test(`register and log in ${TOTAL_USERS} ${STATE} users, logging out between each`, async ({ page }) => {
    test.setTimeout(240_000)

    // Start from a clean Mailpit inbox so fetchMagicLink can't be fooled by
    // stale tokens from earlier runs (tokens are single-use and expire).
    await clearMailpit()

    // Look up a real zipcode (and the state id) for the requested state.
    const { stateId, zipcode } = await resolveLocation()
    console.log(`[state=${STATE}] using zipcode ${zipcode} for ${TOTAL_USERS} users`)

    let n = 0
    for (let i = 1; i <= 2; i++) {
      for (const role of ROLES) {
        n++
        const email = seededEmail(role, i)
        // One phone per user, in a per-state range (state id * 100 + counter),
        // so each clears the UNIQUE phone constraint and states don't overlap.
        // Backend doesn't validate phone format, only uniqueness.
        const phone = String(3030000000 + stateId * 100 + n)

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
        // query the DB and see the state immediately before the final user
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
