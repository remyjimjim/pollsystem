import { test, expect } from '@playwright/test'
import {
  BASE,
  type Location,
  createBallotMeasureAs,
  createElectionAs,
  hold,
  resolveLocation,
  seedUser,
  signInSeededUser,
} from './seed'

// Actor: creator. Journey: from the Creator dashboard, Edit a draft election,
// then Edit one of its draft ballot measures (listed at the bottom of the
// election form): the measure's form opens; "Back to the election" returns to
// the election's form. (Both are the same edit page with different addresses;
// it once kept showing the election.) Setup via the API. Read-mostly.
test.describe('creator edits an election and its measures', () => {
  let loc: Location
  let creatorEmail = ''
  let electionId = 0
  let measureId = 0
  const stamp = Date.now()
  const electionTitle = `E2E Edit Election ${stamp}`
  const measureTitle = `E2E Edit Measure ${stamp}`

  test.beforeAll(async () => {
    loc = await resolveLocation()
    creatorEmail = (await seedUser({ access: 'CREATOR', zipcode: loc.zipcode })).email
    const election = await createElectionAs(creatorEmail, electionTitle, { scopeLevel: 'STATE', regionIds: [loc.stateId] }, { publish: false })
    expect(election.ok, `seed election: ${election.status} ${election.message ?? ''}`).toBe(true)
    electionId = election.id!
    const measure = await createBallotMeasureAs(creatorEmail, electionId, measureTitle, { publish: false })
    expect(measure.ok, `seed measure: ${measure.status} ${measure.message ?? ''}`).toBe(true)
    measureId = measure.id!
  })

  test('Edit on a measure opens the measure; Back to the election returns', async ({ page }) => {
    test.setTimeout(120_000)
    await signInSeededUser(page, creatorEmail)
    await page.goto(`${BASE}/creator/dashboard`)
    await page.locator('tr', { hasText: electionTitle }).getByRole('link', { name: 'Edit' }).click()
    await expect(page).toHaveURL(new RegExp(`/creator/polls/election/${electionId}/edit$`))
    await expect(page.getByLabel('Title')).toHaveValue(electionTitle, { timeout: 15_000 })

    const row = page.locator('[data-test="election-measure"]', { hasText: measureTitle })
    await row.locator('[data-test="edit-measure"]').click()
    await expect(page).toHaveURL(new RegExp(`/creator/polls/ballot-measure/${measureId}/edit$`))
    await expect(page.getByLabel('Title')).toHaveValue(measureTitle, { timeout: 15_000 })
    await hold(page)

    await page.locator('[data-test="back-to-election"]').click()
    await expect(page).toHaveURL(new RegExp(`/creator/polls/election/${electionId}/edit$`))
    await expect(page.getByLabel('Title')).toHaveValue(electionTitle, { timeout: 15_000 })
    await hold(page)
  })
})
