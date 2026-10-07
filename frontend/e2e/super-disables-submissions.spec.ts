import { test, expect } from '@playwright/test'
import { clearMailpit } from './mailpit'
import { BASE, STATE_INPUT, type Location, hold, resolveLocation, seedQuestionnaire, seedUser, setPollsDisabled, signInSeededUser } from './seed'

/**
 * Super-admin global kill-switch: disabling poll submissions makes every
 * submit return 503 while reads stay up; re-enabling restores them.
 *
 * Reuse script (no `registers` in the name) — seeds its own fixtures via the
 * dev API and never pre-wipes. Super and participant use separate browser
 * contexts so their sessions don't collide.
 */
test.describe(`super disables poll submissions (${STATE_INPUT.toLowerCase()})`, () => {
  let superEmail = ''
  let participantEmail = ''
  let pollTitle = ''

  // Safety net: the suite runs on one worker, so a failure between "disable"
  // and "re-enable" would otherwise leave submissions off for every later spec.
  test.afterAll(async () => {
    if (superEmail) await setPollsDisabled(superEmail, false)
  })
  let loc: Location

  test.beforeAll(async () => {
    loc = await resolveLocation()
    superEmail = (await seedUser({ access: 'SUPER', zipcode: loc.zipcode })).email
    participantEmail = (await seedUser({ zipcode: loc.zipcode })).email
    pollTitle = (await seedQuestionnaire()).title
  })

  test('super disables submissions, a member is blocked, then super re-enables', async ({ browser }) => {
    test.setTimeout(180_000)

    // --- Super disables submissions ---
    const superCtx = await browser.newContext()
    const superPage = await superCtx.newPage()
    await clearMailpit()
    await signInSeededUser(superPage, superEmail)
    await superPage.goto(`${BASE}/super/dashboard`)
    await expect(superPage.getByRole('heading', { name: /Super Dashboard/i })).toBeVisible({ timeout: 15_000 })
    await hold(superPage)
    // window.confirm must be accepted BEFORE the click, or Playwright auto-dismisses it.
    superPage.once('dialog', d => d.accept())
    await superPage.getByRole('button', { name: 'Disable all submissions' }).click()
    await expect(superPage.getByText('Submissions DISABLED')).toBeVisible({ timeout: 15_000 })
    await hold(superPage)

    // --- A member's submission is blocked (503) while reads still work ---
    const userCtx = await browser.newContext()
    const userPage = await userCtx.newPage()
    await clearMailpit()
    await signInSeededUser(userPage, participantEmail)
    await userPage.goto(`${BASE}/polls/search`)
    await expect(userPage.getByText('Find a Poll')).toBeVisible({ timeout: 15_000 })
    await userPage.getByLabel('Title contains').fill(pollTitle)
    await userPage.getByRole('button', { name: 'Search' }).click()
    const row = userPage.locator('tr', { hasText: pollTitle })
    await expect(row).toBeVisible({ timeout: 15_000 })
    await row.getByRole('link', { name: /Vote/ }).click()
    await expect(userPage).toHaveURL(/\/polls\/questionnaire\/\d+$/)
    await hold(userPage)
    await userPage.locator('input[type="radio"][value="Yes"]').first().check()
    await userPage.getByRole('button', { name: 'Submit responses' }).click()
    await expect(
      userPage.getByText('Poll submissions are temporarily disabled by an administrator')
    ).toBeVisible({ timeout: 30_000 })
    await hold(userPage)

    // --- Super re-enables submissions ---
    superPage.once('dialog', d => d.accept())
    await superPage.getByRole('button', { name: 'Re-enable submissions' }).click()
    await expect(superPage.getByText('Submissions ENABLED')).toBeVisible({ timeout: 15_000 })
    await hold(superPage)

    await userCtx.close()
    await superCtx.close()
  })
})
