import { test, expect, type Page } from '@playwright/test'
import { BASE } from '../seed'

// Demo (opt-in, never in CI): a member signs in the normal way and answers a
// questionnaire, slowly enough to watch. Run it with scripts/e2e-demo-vote.bash,
// which sets DEMO_E2E=1 and opens a headed browser.
//
// Settings (env):
//   E2E_USER            the member's email (required), e.g. demo-user-wash@test.local
//   E2E_POLL_TITLE      the questionnaire (default: the Seahawks one)
//   E2E_ANSWER          Yes | No, for every question (default Yes)
//   E2E_STEP_PAUSE_MS   pause after each action (default 3000)
//   E2E_LOGIN_DELAY_MS  wait between signing in and going to Find polls (default 4000)

const USER = process.env.E2E_USER ?? ''
const POLL = process.env.E2E_POLL_TITLE ?? 'Will the Seahawks win the super bowl again in 2026/7?'
const ANSWER = process.env.E2E_ANSWER === 'No' ? 'No' : 'Yes'
const PAUSE = Number(process.env.E2E_STEP_PAUSE_MS ?? 3000)
const LOGIN_DELAY = Number(process.env.E2E_LOGIN_DELAY_MS ?? 4000)
const MAILPIT = 'http://localhost:8025'

const pause = (page: Page, ms = PAUSE) => page.waitForTimeout(ms)

/** The newest Mailpit message to [to], or null. */
async function newestMessageId(to: string): Promise<string | null> {
  const r = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${to}`)}&limit=1`)
  const d = await r.json() as { messages?: Array<{ ID: string }> }
  return d.messages?.[0]?.ID ?? null
}

/** The sign-in link from a message to [to] newer than [after] (never an old, used link). */
async function newSignInLink(to: string, after: string | null): Promise<string> {
  for (let n = 0; n < 60; n++) {
    const id = await newestMessageId(to)
    if (id && id !== after) {
      const m = await fetch(`${MAILPIT}/api/v1/message/${id}`).then(r => r.json()) as { Text?: string; HTML?: string }
      const link = (m.Text ?? m.HTML ?? '').match(/http:\/\/localhost:3000\/auth\/magic-link\?token=[^\s"<]+/)
      if (link) return link[0]
    }
    await new Promise(r => setTimeout(r, 500))
  }
  throw new Error(`No new sign-in email for ${to} after 30s (is Mailpit at ${MAILPIT}?)`)
}

test('a member signs in and answers a questionnaire', async ({ page }) => {
  test.skip(!USER, 'Set E2E_USER (scripts/e2e-demo-vote.bash <email>)')
  test.setTimeout(10 * 60_000)

  // 1. Sign in the normal way: Login page, email, the emailed link.
  await page.goto(`${BASE}/login`)
  await pause(page)
  await page.getByLabel('Email').fill(USER)
  await pause(page)
  const before = await newestMessageId(USER)
  await page.getByRole('button', { name: 'Email me a sign-in link' }).click()
  await expect(page.getByText('Check your email.')).toBeVisible({ timeout: 15_000 })
  await pause(page)
  await page.goto(await newSignInLink(USER, before))           // as if clicked in the email
  await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible({ timeout: 30_000 })
  await pause(page, LOGIN_DELAY)

  // 2. Find polls → Search (all polls).
  await page.locator('header a[href="/polls/search"]').click()
  await expect(page).toHaveURL(/\/polls\/search/)
  await pause(page)
  await page.getByRole('button', { name: 'Search', exact: true }).click()
  const row = page.locator('tr', { hasText: POLL })
  await expect(row, `"${POLL}" isn't in the search results (published, and open in ${USER}'s area?)`).toBeVisible({ timeout: 15_000 })
  await pause(page)

  // 3. Vote on it.
  await row.getByRole('link', { name: /Vote/ }).click()
  await expect(page.getByRole('heading', { name: POLL })).toBeVisible({ timeout: 15_000 })
  await pause(page)
  const edit = page.getByRole('button', { name: 'Yes, edit' })
  if (await edit.isVisible()) {                                 // answered before: change the answers
    await edit.click()
    await pause(page)
  }
  const questions = page.locator('fieldset')
  const count = await questions.count()
  for (let i = 0; i < count; i++) {
    await questions.nth(i).getByRole('radio', { name: ANSWER, exact: true }).check()
    await pause(page)
  }
  await page.getByRole('button', { name: /Submit responses|Update responses/ }).click()
  await expect(page).toHaveURL(/\/results/, { timeout: 30_000 })
  await pause(page)
})
