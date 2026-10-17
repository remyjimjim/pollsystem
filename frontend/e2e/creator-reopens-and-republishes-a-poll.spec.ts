import { test, expect } from '@playwright/test'
import { BASE, type Location, createQuestionnaireAs, hold, resolveLocation, seedUser, signInSeededUser } from './seed'

// Actor: creator. Journey: Reopen a published poll from the Creator dashboard
// (back to draft, straight to its edit page), change its title, publish again,
// then open its History: Published (with the title change), Reopened,
// Published. Setup via the API. Read-mostly.
test.describe('creator reopens and republishes a poll', () => {
  let loc: Location
  let creatorEmail = ''
  let pollId = 0
  const stamp = Date.now()
  const title = `E2E Reopen ${stamp}`
  const newTitle = `E2E Reopen v2 ${stamp}`

  test.beforeAll(async () => {
    loc = await resolveLocation()
    creatorEmail = (await seedUser({ access: 'CREATOR', zipcode: loc.zipcode })).email
    const q = await createQuestionnaireAs(creatorEmail, title, { scopeLevel: 'STATE', regionIds: [loc.stateId] })
    pollId = q.id
  })

  test('reopen, revise, publish again; the history shows each step', async ({ page }) => {
    test.setTimeout(150_000)
    await signInSeededUser(page, creatorEmail)
    await page.goto(`${BASE}/creator/dashboard`)
    page.once('dialog', (d) => d.accept())                       // "Reopen … to revise it?"
    await page.locator('tr', { hasText: title }).locator('[data-test="reopen"]').click()
    await expect(page).toHaveURL(new RegExp(`/creator/polls/questionnaire/${pollId}/edit$`), { timeout: 15_000 })

    await page.getByLabel('Title').fill(newTitle)
    await hold(page)
    await page.getByRole('button', { name: 'Publish', exact: true }).click()
    await expect(page).toHaveURL(/\/creator\/dashboard$/, { timeout: 30_000 })

    await page.locator('tr', { hasText: newTitle }).locator('[data-test="poll-history"]').click()
    await expect(page).toHaveURL(new RegExp(`/history\\?type=questionnaire&id=${pollId}$`))
    const rows = page.locator('[data-test="history-row"]')
    await expect(rows).toHaveCount(3, { timeout: 15_000 })
    await expect(rows.nth(0)).toContainText('Published')
    await expect(rows.nth(0)).toContainText(`Title: "${title}" → "${newTitle}"`)
    await expect(rows.nth(1)).toContainText('Reopened')
    await expect(rows.nth(2)).toContainText('Published')
    await hold(page)
  })
})
