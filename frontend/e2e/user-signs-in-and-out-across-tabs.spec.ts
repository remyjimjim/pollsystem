import { test, expect } from '@playwright/test'
import { BASE, hold, seedUser, signInSeededUser } from './seed'
import { fetchMagicLink } from './mailpit'

// Actor: user (a member). The browser's tabs share one login.
// 1. Signing in: the emailed link opens in a new tab; the tab you started from
//    (the Login page, reached from the home page) signs in and moves on too.
// 2. Logging out in one tab logs the other out too, at once and without a
//    reload, so an open tab can't stay usable after "Logout".
// Read-only: no pre-wipe.
test.describe('user signs in and out across tabs', () => {
  let memberEmail = ''

  test.beforeAll(async () => {
    memberEmail = (await seedUser({})).email
  })

  test('the emailed link in a new tab signs in the tab you started from', async ({ page, context }) => {
    test.setTimeout(120_000)
    await page.goto(`${BASE}/`)
    await page.getByRole('link', { name: 'Login' }).first().click()
    await expect(page).toHaveURL(/\/login/)
    await page.getByLabel('Email').fill(memberEmail)
    await page.getByRole('button', { name: 'Email me a sign-in link' }).click()
    await expect(page.getByText('Check your email.')).toBeVisible({ timeout: 15_000 })

    const linkTab = await context.newPage()
    await linkTab.goto(await fetchMagicLink(memberEmail))
    await expect(linkTab.getByRole('button', { name: 'Logout' })).toBeVisible({ timeout: 30_000 })

    // The first tab doesn't stay on the Login page: it's signed in and moves on.
    await expect(page).not.toHaveURL(/\/login/, { timeout: 10_000 })
    await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible()
    await hold(page)
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
