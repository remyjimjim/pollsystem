import { test, expect } from '@playwright/test'
import { clearMailpit } from './mailpit'
import {
  API,
  BASE,
  STATE_INPUT,
  type Location,
  createElectionAs,
  devToken,
  hold,
  resolveLocation,
  seedUser,
  signInSeededUser,
} from './seed'

// Actor: creator. Journey: a ballot measure hangs off one of the creator's own
// elections and inherits its purview. With a published, state-wide election
// already in place (created via the API), the creator builds a measure in the
// poll wizard, attaches it to that election, publishes it, sees it on the
// dashboard, a guest finds it in public search under the election's state, and
// the API shows it attached to the right election.
//
// Read-mostly: the creator is seeded via the API, so per convention no pre-wipe.
test.describe(`creator creates a ballot measure (${STATE_INPUT.toLowerCase()})`, () => {
  let email = ''
  let loc: Location
  const stamp = Date.now()
  const electionTitle = `E2E Parent Election ${stamp}`
  const title = `E2E Creator Ballot Measure ${stamp}`
  // Effective date two months out, as the date input wants it (YYYY-MM-DD).
  const effectiveDate = new Date(Date.now() + 60 * 24 * 3600 * 1000).toISOString().slice(0, 10)

  test.beforeAll(async () => {
    loc = await resolveLocation()
    email = (await seedUser({ access: 'CREATOR', zipcode: loc.zipcode })).email
    const election = await createElectionAs(email, electionTitle, { scopeLevel: 'STATE', regionIds: [loc.stateId] })
    expect(election.ok, `seed election: ${election.status} ${election.message ?? ''}`).toBe(true)
  })

  test('builds, attaches to an election, publishes, and is searchable', async ({ page, browser }) => {
    test.setTimeout(150_000)
    await clearMailpit()

    // 1. Sign in as the seeded creator.
    await signInSeededUser(page, email)

    // 2. Poll wizard → choose the Referendum/Ballot Measure type.
    await page.goto(`${BASE}/creator/polls/new`)
    await expect(page.getByRole('heading', { name: 'Create New Poll' })).toBeVisible({ timeout: 15_000 })
    await page.getByRole('button', { name: 'Referendum/Ballot Measure' }).click()
    await expect(page.getByRole('heading', { name: 'Referendum/Ballot Measure' })).toBeVisible()
    await hold(page)

    // 3. Attach it to the creator's election (options read "Title (STATUS)").
    const parent = page.getByLabel('Parent Election')
    await expect(parent.locator('option', { hasText: electionTitle })).toHaveCount(1, { timeout: 15_000 })
    await parent.selectOption({ label: `${electionTitle} (PUBLISHED)` })
    await hold(page)

    // 4. Title, summary, effective date.
    await page.getByLabel('Title', { exact: true }).fill(title)
    await page.getByLabel('Summary').fill(`Seeded by the creator-creates-ballot-measure e2e for ${loc.stateName}.`)
    await page.getByLabel('Effective date').fill(effectiveDate)
    await hold(page)

    // 5. Publish (saves first). No close date → no "closes soon" prompt.
    await page.getByRole('button', { name: 'Publish', exact: true }).click()
    await expect(page.getByText('Published!')).toBeVisible({ timeout: 30_000 })

    // 6. Dashboard shows it as PUBLISHED.
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

    // 8. Saved as built: published, attached to the election, inheriting its
    //    state-wide purview (search labels it with the election's state).
    const hits = (await (await fetch(`${API}/api/polls/search?${new URLSearchParams({ title })}`)).json()) as Array<{
      id: number; type: string; title: string; regionLabel: string
    }>
    const hit = hits.find((h) => h.title === title && h.type === 'BallotMeasure')
    expect(hit, 'ballot measure in search results').toBeTruthy()
    expect(hit!.regionLabel).toBe(loc.stateName)
    const measure = (await (await fetch(`${API}/api/polls/ballot-measures/${hit!.id}`, {
      headers: { Authorization: `Bearer ${await devToken(email)}` },
    })).json()) as { status: string; electionTitle: string; effectiveDate: string }
    expect(measure.status).toBe('PUBLISHED')
    expect(measure.electionTitle).toBe(electionTitle)
    expect(measure.effectiveDate).toBe(effectiveDate)
  })
})
