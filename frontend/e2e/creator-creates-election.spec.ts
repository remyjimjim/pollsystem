import { test, expect } from '@playwright/test'
import { clearMailpit } from './mailpit'
import {
  API,
  BASE,
  STATE_INPUT,
  type Location,
  devToken,
  hold,
  resolveLocation,
  seedUser,
  signInSeededUser,
} from './seed'

// Actor: creator. Journey: sign in, build an ELECTION for a whole state in the
// poll wizard (date + two candidates), publish it, see it on the dashboard,
// confirm a guest finds it in public search, and check through the API that
// both candidates were saved. Elections take a County / State / Nationwide
// purview (no zipcodes) and need at least one complete candidate.
//
// Read-mostly: the creator is seeded via the API (nationwide creator access),
// so per convention it does NOT pre-wipe.
test.describe(`creator creates an election (${STATE_INPUT.toLowerCase()})`, () => {
  let email = ''
  let loc: Location
  const title = `E2E Creator Election ${Date.now()}`
  // An election date a month out, as the date input wants it (YYYY-MM-DD).
  const electionDate = new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString().slice(0, 10)
  const candidates = [
    { name: 'Avery Stone', affiliation: 'Independent', office: 'Mayor' },
    { name: 'Jordan Reyes', affiliation: 'Civic Party', office: 'Mayor' },
  ]

  test.beforeAll(async () => {
    loc = await resolveLocation()
    email = (await seedUser({ access: 'CREATOR', zipcode: loc.zipcode })).email
  })

  test('builds, publishes, and the election is searchable with both candidates', async ({ page, browser }) => {
    test.setTimeout(150_000)
    await clearMailpit()

    // 1. Sign in as the seeded creator.
    await signInSeededUser(page, email)

    // 2. Poll wizard → choose the Election type.
    await page.goto(`${BASE}/creator/polls/new`)
    await expect(page.getByRole('heading', { name: 'Create New Poll' })).toBeVisible({ timeout: 15_000 })
    await page.getByRole('button', { name: 'Election' }).click()
    await expect(page.getByRole('heading', { name: 'Election' })).toBeVisible()
    await hold(page)

    // 3. Title + election date.
    await page.getByLabel('Title', { exact: true }).fill(title)
    await page.getByLabel('Election date').fill(electionDate)

    // 4. Purview: the whole resolved state (elections offer County/State/Nationwide).
    await page.getByRole('radio', { name: 'Whole state(s)' }).check()
    await page.getByRole('checkbox', { name: new RegExp('^' + loc.stateName) }).check()
    await hold(page)

    // 5. Candidates: fill the first row, add a second (rows share placeholders).
    for (const [i, c] of candidates.entries()) {
      if (i > 0) await page.getByRole('button', { name: '+ Add candidate' }).click()
      await page.getByPlaceholder('Name', { exact: true }).nth(i).fill(c.name)
      await page.getByPlaceholder('Affiliation', { exact: true }).nth(i).fill(c.affiliation)
      await page.getByPlaceholder('Office (e.g. Mayor)').nth(i).fill(c.office)
    }
    await hold(page)

    // 6. Publish (saves first). No close date → no "closes soon" prompt.
    await page.getByRole('button', { name: 'Publish', exact: true }).click()
    await expect(page.getByText('Published!')).toBeVisible({ timeout: 30_000 })

    // 7. Dashboard shows it as PUBLISHED.
    await expect(page).toHaveURL(/\/creator\/dashboard/, { timeout: 15_000 })
    const row = page.locator('tr', { hasText: title })
    await expect(row).toBeVisible({ timeout: 15_000 })
    await expect(row).toContainText('PUBLISHED')
    await hold(page)

    // 8. A guest (fresh, signed-out context) finds it in public search.
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

    // 9. Saved as built: state-wide, published, with both candidates and offices.
    const hits = (await (await fetch(`${API}/api/polls/search?${new URLSearchParams({ title })}`)).json()) as Array<{
      id: number; type: string; title: string
    }>
    const hit = hits.find((h) => h.title === title && h.type === 'Election')
    expect(hit, 'election in search results').toBeTruthy()
    const election = (await (await fetch(`${API}/api/polls/elections/${hit!.id}`, {
      headers: { Authorization: `Bearer ${await devToken(email)}` },
    })).json()) as {
      status: string; scopeLevel: string; regionLabel: string; date: string
      candidates: Array<{ name: string; affiliation: string; officeName: string }>
    }
    expect(election.status).toBe('PUBLISHED')
    expect(election.scopeLevel).toBe('STATE')
    expect(election.regionLabel).toBe(loc.stateName)
    expect(election.date).toBe(electionDate)
    expect(election.candidates.map((c) => [c.name, c.affiliation, c.officeName]).sort())
      .toEqual(candidates.map((c) => [c.name, c.affiliation, c.office]).sort())
  })
})
