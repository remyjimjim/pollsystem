import { test, expect } from '@playwright/test'
import { clearMailpit } from './mailpit'
import {
  BASE,
  STATE_INPUT,
  type Location,
  hold,
  resolveLocation,
  seedUser,
  signInSeededUser,
} from './seed'

// Actor: creator. Journey: sign in, build a questionnaire for a WHOLE STATE in
// the poll wizard, publish it, see it on the creator dashboard, then confirm a
// guest can find it in public search — i.e. it really went live.
//
// Poll writes are gated by the creator's access (CreatorGrantGuard); the seeded
// CREATOR gets nationwide access, so publishing for any state is allowed.
// Read-mostly: the creator is seeded via the API (fresh, uniquely-named each
// run), so per convention it does NOT pre-wipe.
test.describe(`creator creates a questionnaire (${STATE_INPUT.toLowerCase()})`, () => {
  let email = ''
  let loc: Location
  // Unique per run so the dashboard row and the search hit are unambiguous.
  const title = `E2E Creator Questionnaire ${Date.now()}`

  test.beforeAll(async () => {
    loc = await resolveLocation()
    email = (await seedUser({ access: 'CREATOR', zipcode: loc.zipcode })).email
  })

  test('builds, publishes, and the questionnaire is publicly searchable', async ({ page, browser }) => {
    test.setTimeout(150_000)
    await clearMailpit()

    // 1. Sign in as the seeded creator (reuse pattern: no registration).
    await signInSeededUser(page, email)

    // 2. Poll wizard → choose the Questionnaire type.
    await page.goto(`${BASE}/creator/polls/new`)
    await expect(page.getByRole('heading', { name: 'Create New Poll' })).toBeVisible({ timeout: 15_000 })
    await page.getByRole('button', { name: 'Questionnaire' }).click()
    await expect(page.getByRole('heading', { name: 'Questionnaire' })).toBeVisible()
    await hold(page)

    // 3. Purview: the whole resolved state (checkbox label is "Name (XX)").
    await page.getByRole('radio', { name: 'Whole state(s)' }).check()
    await page.getByRole('checkbox', { name: new RegExp('^' + loc.stateName) }).check()
    await hold(page)

    // 4. Content: title, summary, two questions (the second via "+ Add question").
    await page.getByLabel('Title', { exact: true }).fill(title)
    await page.getByLabel('Summary').fill(`Seeded by the creator-creates-questionnaire e2e for ${loc.stateName}.`)
    await page.getByPlaceholder('Question 1').fill('Should the library open on Sundays?')
    await page.getByRole('button', { name: '+ Add question' }).click()
    await page.getByPlaceholder('Question 2').fill('Should parking near the library be free?')
    await hold(page)

    // 5. Publish (saves the draft first). No close date → no "closes soon" prompt.
    await page.getByRole('button', { name: 'Publish', exact: true }).click()
    await expect(page.getByText('Published!')).toBeVisible({ timeout: 30_000 })

    // 6. Redirected to the dashboard, where the poll shows as PUBLISHED.
    await expect(page).toHaveURL(/\/creator\/dashboard/, { timeout: 15_000 })
    const row = page.locator('tr', { hasText: title })
    await expect(row).toBeVisible({ timeout: 15_000 })
    await expect(row).toContainText('PUBLISHED')
    await hold(page)

    // 7. A guest (fresh, signed-out context) finds it in public search.
    const guest = await browser.newPage()
    try {
      await guest.goto(`${BASE}/polls/search`)
      await expect(guest.getByText('Browse Poll Results')).toBeVisible({ timeout: 15_000 })
      await guest.getByLabel('Title contains').fill(title)
      await guest.getByRole('button', { name: 'Search' }).click()
      await expect(guest.locator('tr', { hasText: title })).toBeVisible({ timeout: 15_000 })
      await hold(guest)
    } finally {
      await guest.close()
    }
  })
})
