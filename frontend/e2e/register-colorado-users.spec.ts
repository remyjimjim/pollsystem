import { test, expect, type Page } from '@playwright/test'
import { clearMailpit, fetchMagicLink } from './mailpit'

// Role string is part of the email handle for traceability only. The
// register form has no role field; access level is granted later via
// AdminRequest approval.
const ROLES = ['user', 'viewer', 'creator', 'admin'] as const

// Inject a visible HTML modal with a Close button and block the test until
// the user clicks Close. Playwright auto-dismisses native alert() calls, so
// we render our own DOM overlay we can deterministically wait on.
async function pauseWithModal(page: Page, message: string) {
  await page.evaluate((msg) => {
    const overlay = document.createElement('div')
    overlay.id = '__e2e_pause_overlay'
    overlay.style.cssText = [
      'position:fixed', 'inset:0', 'z-index:2147483647',
      'background:rgba(0,0,0,0.6)',
      'display:flex', 'align-items:center', 'justify-content:center',
    ].join(';')
    overlay.innerHTML = `
      <div style="background:#fff;padding:24px 28px;border-radius:8px;max-width:560px;
                  font-family:system-ui,sans-serif;box-shadow:0 10px 25px rgba(0,0,0,0.3);">
        <h2 style="margin:0 0 12px;font-size:18px;font-weight:600;color:#0f172a;">${msg}</h2>
        <p style="margin:0 0 12px;font-size:13px;color:#475569;line-height:1.5;">
          The test is paused. Query the dev database now if you need to. Connect with:
        </p>
        <code style="background:#f1f5f9;padding:6px 8px;border-radius:3px;display:block;
                     font-size:12px;color:#0f172a;white-space:pre;overflow-x:auto;">
PGPASSWORD=pollpass123 psql -h localhost -U polladmin -d pollsystem</code>
        <p style="margin:12px 0 16px;font-size:12px;color:#64748b;line-height:1.4;">
          Note: this is the local-dev DB <code>pollsystem</code>. The
          Testcontainers <code>pollsystem_test</code> DB only exists during
          <code>./gradlew test</code> runs.
        </p>
        <button id="__e2e_pause_close"
                style="background:#0f172a;color:#fff;border:none;padding:8px 18px;
                       border-radius:4px;font-size:14px;font-weight:500;cursor:pointer;">
          Close
        </button>
      </div>
    `
    document.body.appendChild(overlay)
    ;(window as unknown as { __e2e_paused: boolean }).__e2e_paused = true
    document.getElementById('__e2e_pause_close')!.addEventListener('click', () => {
      overlay.remove()
      ;(window as unknown as { __e2e_paused: boolean }).__e2e_paused = false
    })
  }, message)

  // Long timeout — wait for the human to click Close.
  await page.waitForFunction(
    () => !(window as unknown as { __e2e_paused: boolean }).__e2e_paused,
    undefined,
    { timeout: 30 * 60_000 }
  )
}

const TOTAL_USERS = 2 * ROLES.length

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
          // 1. Home → Register CTA (disambiguated from the nav link by the trailing arrow)
          await page.goto('http://localhost:3000')
          await page.getByRole('link', { name: 'Register →' }).click()
          await expect(page).toHaveURL('http://localhost:3000/register')

          // 2. Fill the form and request the magic link.
          await page.getByLabel('Email').fill(email)
          await page.getByLabel('Phone').fill(phone)
          await page.getByLabel('Zipcode').fill(zipcode)

          // On the very last iteration, pause before submit so the user can
          // query the DB and see the state immediately before the 8th user
          // is registered. Resume by clicking Close in the injected modal.
          if (n === TOTAL_USERS) {
            await pauseWithModal(page, 'Last chance to query database')
          }

          await page.getByRole('button', { name: 'Email me a sign-in link' }).click()
          // SMTP send through Mailpit can take >5s; bump the assertion wait.
          await expect(page.getByText('Check your email.')).toBeVisible({ timeout: 30_000 })

          // 3. Pull the magic link out of Mailpit's API and visit it.
          const magicHref = await fetchMagicLink(email)
          await page.goto(magicHref)
          await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible({ timeout: 30_000 })
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
