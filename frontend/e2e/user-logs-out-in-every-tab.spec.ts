import { test, expect } from '@playwright/test'
import { BASE, hold, seedUser, signInSeededUser } from './seed'

// Actor: user (a member). Journey: signed in, with the site open in a second
// tab (same browser, so the same login). Logging out in one tab logs the other
// out too, at once and without a reload, so an open tab can't stay usable after
// "Logout" (e.g. on a shared computer). Read-only: no pre-wipe.
test.describe('user logs out in every tab', () => {
  let memberEmail = ''

  test.beforeAll(async () => {
    memberEmail = (await seedUser({})).email
  })

  test('logging out in one tab logs the other tab out too', async ({ page, context }) => {
    test.setTimeout(120_000)
    await signInSeededUser(page, memberEmail)

    // A second tab picks up the same login.
    const other = await context.newPage()
    await other.goto(`${BASE}/creator/request`)
    await expect(other.getByRole('button', { name: 'Logout' })).toBeVisible({ timeout: 15_000 })
    await hold(other)

    // Log out in the first tab…
    await page.getByRole('button', { name: 'Logout' }).click()
    await expect(page.getByRole('button', { name: 'Logout' })).toHaveCount(0)

    // …and the second tab follows on its own: logged out, sent home.
    await expect(other.getByRole('button', { name: 'Logout' })).toHaveCount(0, { timeout: 10_000 })
    await expect(other).toHaveURL(new RegExp(`^${BASE}/?$`))
    await hold(other)
  })
})
