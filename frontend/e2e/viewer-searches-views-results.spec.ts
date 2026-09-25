import { test, expect } from '@playwright/test'
import {
  BASE,
  STATE_INPUT,
  type Location,
  hold,
  resolveLocation,
  seedBallotMeasure,
  seedBallotResponses,
} from './seed'

// Actor: viewer (a GUEST — not logged in). Journey: search for a ballot measure
// by title, open its results, and see the k-anonymity privacy floor in action —
// the default (all-respondents) view shows the tally, but narrowing to the
// poll's purview WITHHOLDS it, because there are fewer than the threshold (10)
// in-purview responses.
//
// Read-only actor, so per the seed conventions it does NOT pre-wipe; it seeds
// fresh, uniquely-titled data each run (searched by that exact title). Doubles
// as a screen-recordable how-to: pass hold=3000 (or higher) for a calm video.
test.describe(`viewer searches and views ballot-measure results (${STATE_INPUT.toLowerCase()})`, () => {
  let title = ''
  let loc: Location

  test.beforeAll(async () => {
    loc = await resolveLocation()
    // Seed a published ballot measure, then 3 real registered voters in the
    // SAME zip — in-purview but well under the k-anonymity threshold.
    const bm = await seedBallotMeasure({ zipcode: loc.zipcode })
    title = bm.title
    await seedBallotResponses({ measureId: bm.id, count: 3, zipcode: loc.zipcode })
  })

  test('guest finds the measure, sees the open tally, then the purview view withholds it', async ({ page }) => {
    test.setTimeout(120_000)

    // 1. Public search page (guest heading confirms we're not authenticated).
    await page.goto(`${BASE}/polls/search`)
    await expect(page.getByText('Browse Poll Results')).toBeVisible({ timeout: 15_000 })
    await hold(page)

    // 2. Find the seeded measure by its unique title.
    await page.getByLabel('Title contains').fill(title)
    await page.getByRole('button', { name: 'Search' }).click()
    const row = page.locator('tr', { hasText: title })
    await expect(row).toBeVisible({ timeout: 15_000 })
    await hold(page)

    // 3. Open results — a guest row shows only "View results" (no "Vote").
    await row.getByRole('link', { name: 'View results' }).click()
    await expect(page).toHaveURL(/\/polls\/ballot-measure\/\d+\/results/)
    // The purview checkbox only renders once results data has loaded.
    const purview = page.getByRole('checkbox', { name: /Only voters from poll/i })
    await expect(purview).toBeVisible({ timeout: 15_000 })
    await hold(page)

    // 4. Default view (all respondents): below-threshold data is NOT withheld,
    //    so the privacy banner is absent.
    const withheld = page.getByText(/Not enough responses in this group/)
    await expect(withheld).toBeHidden()
    await hold(page)

    // 5. Narrow to the poll's purview → now under the k-anonymity threshold →
    //    the tally is withheld behind the privacy-protection banner.
    await purview.check()
    await expect(withheld).toBeVisible({ timeout: 15_000 })
    await hold(page)
  })
})
