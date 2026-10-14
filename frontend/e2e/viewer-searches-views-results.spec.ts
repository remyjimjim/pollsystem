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
// by title, open its results, and see small-poll privacy in action:
//   - with 6 votes, no results yet: the privacy caption and "6 people have
//     responded so far";
//   - once 12 have voted, results appear as rounded percentages only (they
//     update in steps of 10);
//   - narrowing to people outside the poll's area (none) withholds the result.
//
// Read-only actor, so per the seed conventions it does NOT pre-wipe; it seeds
// fresh, uniquely-titled data each run (searched by that exact title). Doubles
// as a screen-recordable how-to: pass hold=3000 (or higher) for a calm video.
test.describe(`viewer searches and views ballot-measure results (${STATE_INPUT.toLowerCase()})`, () => {
  let title = ''
  let measureId = 0
  let loc: Location

  test.beforeAll(async () => {
    loc = await resolveLocation()
    // A published ballot measure, and 6 real registered voters in its zip
    // (alternating Yes / No): under the 10 needed before results show.
    const bm = await seedBallotMeasure({ zipcode: loc.zipcode })
    title = bm.title
    measureId = bm.id
    await seedBallotResponses({ measureId, count: 6, zipcode: loc.zipcode })
  })

  test('guest finds the measure; results stay hidden until 10 have voted, then show as percentages', async ({ page }) => {
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

    // 4. 6 votes: hidden. The caption says why, and how many have responded.
    const caption = page.getByText(/For privacy, results aren't shown until at least 10 people have voted/)
    await expect(caption).toBeVisible({ timeout: 15_000 })
    await expect(page.getByText('6 people have responded so far.')).toBeVisible()
    await expect(page.getByText('%', { exact: false })).toHaveCount(0)
    await hold(page)

    // 5. Six more vote (12 in all): results appear, as rounded percentages only.
    await seedBallotResponses({ measureId, count: 6, zipcode: loc.zipcode, prefix: 'zzz-more' })
    await page.reload()
    await expect(caption).toBeVisible({ timeout: 15_000 })
    await expect(page.getByText('50%')).toHaveCount(2) // 6 Yes, 6 No
    await expect(page.getByText(/respondent\(s\)/)).toHaveCount(0) // no raw counts for the public
    await hold(page)

    // 6. Everyone is inside the poll's area, so the "outside" group is empty →
    //    narrowing to it withholds the result.
    const within = page.getByRole('checkbox', { name: /within the poll's purview/i })
    await within.uncheck()
    await expect(page.getByText(/Not enough responses in this group/)).toBeVisible({ timeout: 15_000 })
    await hold(page)
  })
})
