import { test, expect } from '@playwright/test'
import { BASE, hold, seedUser } from './seed'
import { fetchSignInCode } from './mailpit'

// Actor: user (a member), on a phone. The email app opens links in its own
// browser, so the link can't sign in the browser they started in; instead they
// type the email's 6-digit code there (decided 2026-10-27).
// Read-only: no pre-wipe.
test.use({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })

test.describe('user signs in with the emailed code on a phone', () => {
  let memberEmail = ''

  test.beforeAll(async () => {
    memberEmail = (await seedUser({})).email
  })

  test('a wrong code is refused; the code from the email signs in this browser', async ({ page }) => {
    test.setTimeout(120_000)
    await page.goto(`${BASE}/login`)
    await page.getByLabel('Email').fill(memberEmail)
    await page.getByRole('button', { name: 'Email me a sign-in link' }).click()
    await expect(page.getByText('Check your email.')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByText('On a phone? Type the code from the email:')).toBeVisible()

    const code = await fetchSignInCode(memberEmail)
    const wrong = String((Number(code) + 1) % 1_000_000).padStart(6, '0')
    const box = page.getByLabel('Sign-in code')
    await box.fill(wrong)
    await page.getByRole('button', { name: 'Sign in', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('wrong or has expired')
    await hold(page)

    await box.fill(`${code.slice(0, 3)} ${code.slice(3)}`)   // as the email shows it
    await page.getByRole('button', { name: 'Sign in', exact: true }).click()
    await expect(page).not.toHaveURL(/\/login/, { timeout: 15_000 })
    await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible()
    await hold(page)
  })
})
