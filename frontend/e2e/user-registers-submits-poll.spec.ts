import { test, expect } from '@playwright/test'
import { clearMailpit } from './mailpit'
import {
  BASE,
  KEEP,
  STATE_INPUT,
  type Location,
  hold,
  registerAndSignIn,
  resetTestUsers,
  resolveLocation,
  seedQuestionnaire,
  seededEmail,
  signInSeededUser,
} from './seed'

// Actor: "user" (a paid member). Journey: register + pay, then find a poll by
// title and submit a response — the full sign-up-through-participation path.
//
// The name contains "registers", so it PRE-WIPES its user in beforeAll (a clean
// slate to register into). keep=yes skips the pre-wipe and instead REUSES the
// already-seeded user #1 (signs in rather than re-registering) — handy for
// chaining after the bulk seeder. Place the user with state=<name> /
// county=<name>; see ./seed for the token + reset conventions.
test.describe(`user registers and submits a poll (${STATE_INPUT.toLowerCase()})`, () => {
  const email = seededEmail('user', 1) // zzz1-testuser-<state>@protonmail.com
  let pollTitle = ''
  let location: Location

  test.beforeAll(async () => {
    // This script registers its user, so pre-wipe leftover zzz users — unless
    // keep=yes, which layers onto the existing seed instead.
    if (KEEP) console.log(`[keep] reusing seeded user ${email} (no pre-wipe)`)
    else await resetTestUsers('zzz')

    // Seed a published questionnaire to find + answer. Its title carries a
    // unique suffix so a title search matches exactly this poll.
    pollTitle = (await seedQuestionnaire('zzz')).title
    // A real zipcode (+ state id) for the actor's state/county.
    location = await resolveLocation()
  })

  test('registers/pays (or reuses the seed), finds the poll by title, and submits', async ({ page }) => {
    test.setTimeout(120_000)
    await clearMailpit()

    if (KEEP) {
      // Reuse the already-seeded user — sign in, don't re-register.
      await signInSeededUser(page, email)
    } else {
      // Fresh register + pay (mock Stripe in local / local-docker). Phone in a
      // per-state range distinct from the bulk seeder's; only one user here.
      const phone = String(3120000000 + location.stateId)
      await registerAndSignIn(page, { email, phone, zipcode: location.zipcode })
    }

    // Search for the seeded poll by its unique title (title search isn't
    // zip-scoped, so the actor's own zipcode doesn't need to match the poll's).
    await page.goto(`${BASE}/polls/search`)
    await expect(page.getByText('Find a Poll')).toBeVisible({ timeout: 15_000 })
    await hold(page) // the search page
    await page.getByLabel('Title contains').fill(pollTitle)
    await page.getByRole('button', { name: 'Search' }).click()

    // Open it via its Vote link and submit a response.
    const row = page.locator('tr', { hasText: pollTitle })
    await expect(row).toBeVisible({ timeout: 15_000 })
    await hold(page) // the search result row
    await row.getByRole('link', { name: /Vote/ }).click()
    await expect(page).toHaveURL(/\/polls\/questionnaire\/\d+$/)
    await page.locator('input[type="radio"][value="Yes"]').first().check()
    await hold(page) // the answered question
    await page.getByRole('button', { name: 'Submit responses' }).click()

    await expect(page.getByText('Responses submitted successfully!')).toBeVisible({ timeout: 30_000 })
    await hold(page) // the success confirmation

  })
})
