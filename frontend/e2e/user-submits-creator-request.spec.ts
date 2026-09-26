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

// Actor: user (an existing registered member). Journey: sign in and submit a
// creator request for a WHOLE STATE via the new scope picker — no
// re-registration (reuse pattern). Exercises the scope-levels feature end to end.
//
// Read-mostly: it seeds its actor via the API (a fresh, uniquely-named active
// member each run), so per convention it does NOT pre-wipe.
test.describe(`user submits a creator request (${STATE_INPUT.toLowerCase()})`, () => {
  let email = ''
  let loc: Location

  test.beforeAll(async () => {
    loc = await resolveLocation()
    // An active member to act as (access USER, paid) — created via the API.
    email = (await seedUser({ zipcode: loc.zipcode })).email
  })

  test('signs in and requests creator access for a whole state', async ({ page }) => {
    test.setTimeout(120_000)
    await clearMailpit()

    // Reuse the seeded member — sign in, don't register.
    await signInSeededUser(page, email)
    await hold(page)

    // Go straight to the creator-request form (route needs only a signed-in USER).
    await page.goto(`${BASE}/creator/request`)
    await expect(page.getByRole('heading', { name: /Creator Request/i })).toBeVisible({ timeout: 15_000 })
    await hold(page)

    // Scope = whole state → pick the resolved state (label is "Name (XX)").
    await page.getByRole('radio', { name: 'Whole state(s)' }).check()
    await page.getByRole('checkbox', { name: new RegExp('^' + loc.stateName) }).check()
    await hold(page)

    // At least one poll type.
    await page.getByRole('checkbox', { name: 'Questionnaire' }).check()
    await hold(page)

    await page.getByRole('button', { name: 'Submit Request' }).click()

    // Confirmation — no "Select at least one zipcode" wall for a state-scoped request.
    await expect(page.getByText(/request has been submitted/i)).toBeVisible({ timeout: 30_000 })
    await hold(page)
  })
})
