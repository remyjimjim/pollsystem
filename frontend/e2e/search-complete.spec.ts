import { test, expect, type Page } from '@playwright/test'
import { clearMailpit, fetchMagicLink } from './mailpit'

const BASE = 'http://localhost:3000'
const API = 'http://localhost:8080'

// Register a fresh user through the UI and sign in via the Mailpit magic link.
// The registered user supplies phone + zipcode, so their profile is complete and
// they're allowed to participate (see the phase-3 completion guard).
async function registerAndSignIn(
  page: Page,
  email: string,
  phone: string,
  zipcode: string
): Promise<void> {
  await page.goto(`${BASE}/register`)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Phone').fill(phone)
  await page.getByLabel('Zipcode').fill(zipcode)
  await page.getByRole('button', { name: 'Email me a sign-in link' }).click()
  await expect(page.getByText('Check your email.')).toBeVisible({ timeout: 30_000 })

  const magicHref = await fetchMagicLink(email)
  await page.goto(magicHref)
  await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible({ timeout: 30_000 })
}

test.describe('search for a poll and complete it', () => {
  let pollTitle = ''

  test.beforeAll(async () => {
    // Clear prior test users — this cascades and removes any polls they seeded.
    const reset = await fetch(`${API}/api/dev/reset-test-users?emailPrefix=zzz`, { method: 'POST' })
    if (!reset.ok) throw new Error(`reset-test-users failed: ${reset.status}`)

    // Seed a published questionnaire (zzz creator) to find and answer. Its title
    // carries a unique suffix so a title search matches exactly this poll.
    const res = await fetch(`${API}/api/dev/seed-questionnaire?emailPrefix=zzz`, { method: 'POST' })
    if (!res.ok) throw new Error(`seed-questionnaire failed: ${res.status} ${await res.text()}`)
    pollTitle = (await res.json()).title
  })

  test('registered user searches by title, opens the poll, and submits a response', async ({ page }) => {
    test.setTimeout(120_000)
    await clearMailpit()

    await registerAndSignIn(page, 'zzz1searchcomplete@colorado.com', '3035550199', '80005')

    // Go to search; wait for the authenticated heading so the Vote links render.
    await page.goto(`${BASE}/polls/search`)
    await expect(page.getByText('Find a Poll')).toBeVisible({ timeout: 15_000 })

    // Search by the seeded poll's unique title.
    await page.getByLabel('Title contains').fill(pollTitle)
    await page.getByRole('button', { name: 'Search' }).click()

    // The seeded poll appears in a result row; open it via its Vote link.
    const row = page.locator('tr', { hasText: pollTitle })
    await expect(row).toBeVisible({ timeout: 15_000 })
    await row.getByRole('link', { name: /Vote/ }).click()

    // On the poll detail, answer the (single) question and submit.
    await expect(page).toHaveURL(/\/polls\/questionnaire\/\d+$/)
    await page.locator('input[type="radio"][value="Yes"]').first().check()
    await page.getByRole('button', { name: 'Submit responses' }).click()

    // Success confirmation from the response form.
    await expect(page.getByText('Responses submitted successfully!')).toBeVisible({ timeout: 30_000 })
  })
})
