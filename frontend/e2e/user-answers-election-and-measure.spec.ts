import { test, expect, type Page } from '@playwright/test'
import { clearMailpit } from './mailpit'
import {
  BASE,
  STATE_INPUT,
  type Location,
  createBallotMeasureAs,
  createElectionAs,
  resultsAs,
  hold,
  resolveLocation,
  seedUser,
  signInSeededUser,
} from './seed'

// Actor: user (a paid member in the state). Journey: find a state-wide
// election by title and vote for a candidate, then find a ballot measure on
// that election and vote Yes — and confirm through the public results that
// both votes were counted (an unfiltered results view shows real counts).
//
// Setup via the API: a creator publishes the election (two Mayor candidates)
// and the measure. The local Election type uses the selectOneRadio widget (one
// choice per office, each radio labelled by its candidate). Read-mostly: no
// pre-wipe.
test.describe(`user answers an election and a ballot measure (${STATE_INPUT.toLowerCase()})`, () => {
  let loc: Location
  let memberEmail = ''
  let electionId = 0
  let measureId = 0
  let superEmail = ''
  const stamp = Date.now()
  const electionTitle = `E2E Answer Election ${stamp}`
  const measureTitle = `E2E Answer Measure ${stamp}`

  test.beforeAll(async () => {
    loc = await resolveLocation()
    const creatorEmail = (await seedUser({ access: 'CREATOR', zipcode: loc.zipcode })).email
    const election = await createElectionAs(creatorEmail, electionTitle, { scopeLevel: 'STATE', regionIds: [loc.stateId] }, {
      candidates: [
        { name: 'Avery Stone', affiliation: 'Independent', officeName: 'Mayor' },
        { name: 'Jordan Reyes', affiliation: 'Civic Party', officeName: 'Mayor' },
      ],
    })
    expect(election.ok, `seed election: ${election.status} ${election.message ?? ''}`).toBe(true)
    electionId = election.id!
    const measure = await createBallotMeasureAs(creatorEmail, electionId, measureTitle)
    expect(measure.ok, `seed measure: ${measure.status} ${measure.message ?? ''}`).toBe(true)
    measureId = measure.id!
    memberEmail = (await seedUser({ zipcode: loc.zipcode })).email
    // Counts are for supers only (small-poll privacy); the check below reads as one.
    superEmail = (await seedUser({ access: 'SUPER', zipcode: loc.zipcode })).email
  })

  /** Search by exact title and follow the row's Vote link. */
  async function openFromSearch(page: Page, title: string, path: RegExp) {
    await page.goto(`${BASE}/polls/search`)
    await expect(page.getByText('Find a Poll')).toBeVisible({ timeout: 15_000 })
    await page.getByLabel('Title contains').fill(title)
    await page.getByRole('button', { name: 'Search' }).click()
    const row = page.locator('tr', { hasText: title })
    await expect(row).toBeVisible({ timeout: 15_000 })
    await hold(page)
    await row.getByRole('link', { name: /Vote/ }).click()
    await expect(page).toHaveURL(path, { timeout: 15_000 })
  }

  test('votes in the election and on the measure; both are counted', async ({ page }) => {
    test.setTimeout(180_000)
    await clearMailpit()
    await signInSeededUser(page, memberEmail)

    // 1. Election: one choice for Mayor. Each radio is labelled by its
    //    candidate's name + affiliation, so pick it by name.
    await openFromSearch(page, electionTitle, new RegExp(`/polls/election/${electionId}$`))
    await expect(page.getByRole('heading', { name: electionTitle })).toBeVisible({ timeout: 15_000 })
    await page.getByRole('radio', { name: /Avery Stone/ }).check()
    await expect(page.getByRole('radio', { name: /Jordan Reyes/ })).not.toBeChecked()
    await hold(page)
    await page.getByRole('button', { name: 'Submit votes' }).click()
    // The success message shows for ~600 ms, then the form moves to the results
    // page — assert that redirect (definitive) rather than the brief message.
    await expect(page).toHaveURL(new RegExp(`/polls/election/${electionId}/results`), { timeout: 30_000 })
    await hold(page)

    // 2. Ballot measure: vote Yes.
    await openFromSearch(page, measureTitle, new RegExp(`/polls/ballot-measure/${measureId}$`))
    await expect(page.getByRole('heading', { name: measureTitle })).toBeVisible({ timeout: 15_000 })
    await expect(page.getByText(electionTitle)).toBeVisible() // "Part of <election>"
    await page.getByRole('radio', { name: 'Yes', exact: true }).check()
    await hold(page)
    await page.getByRole('button', { name: 'Submit vote' }).click()
    await expect(page).toHaveURL(new RegExp(`/polls/ballot-measure/${measureId}/results`), { timeout: 30_000 })
    await hold(page)

    // 3. Both votes counted. With one voter the public sees nothing yet (small-poll
    //    privacy), so check the live counts as a super.
    const er = await resultsAs<{ totalRespondents: number; perCandidate: Array<{ name: string; yes: number }> }>(
      superEmail, 'elections', electionId)
    expect(er.totalRespondents).toBe(1)
    const yesBy = Object.fromEntries(er.perCandidate.map((c) => [c.name, c.yes]))
    expect(yesBy['Avery Stone']).toBe(1)
    expect(yesBy['Jordan Reyes']).toBe(0)

    const mr = await resultsAs<{ totalRespondents: number; yes: number; no: number }>(superEmail, 'ballot-measures', measureId)
    expect([mr.totalRespondents, mr.yes, mr.no]).toEqual([1, 1, 0])

    // 4. Revisiting the election shows the member's existing vote.
    await page.goto(`${BASE}/polls/election/${electionId}`)
    await expect(page.getByText('You voted on')).toBeVisible({ timeout: 15_000 })
    await hold(page)
  })
})
