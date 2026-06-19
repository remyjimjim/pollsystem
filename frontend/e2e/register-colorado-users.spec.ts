import { test, expect } from '@playwright/test'
import { clearMailpit, fetchMagicLink } from './mailpit'

test.describe('register Colorado users via magic link', () => {
  test('register and log in 10 users in isolated browser contexts', async ({ browser }) => {
    test.setTimeout(240_000)

    // Start from a clean Mailpit inbox so fetchMagicLink can't be fooled by
    // stale tokens from earlier runs (tokens are single-use and expire).
    await clearMailpit()

    for (let i = 1; i <= 10; i++) {
      const email   = `zzz${i}testuser@colorado.com`
      const phone   = String(3031111110 + i)
      const zipcode = String(80001 + i)

      // Each iteration owns its own browser context = isolated cookies,
      // localStorage, sessionStorage. That's what makes the sessions
      // separate; browser.newPage() alone would share storage and one
      // iteration's login would bleed into the next.
      const ctx = await browser.newContext()
      const page = await ctx.newPage()

      try {
        // 1. Home → Register CTA (disambiguated from the nav link by the trailing arrow)
        await page.goto('http://localhost:3000')
        await page.getByRole('link', { name: 'Register →' }).click()
        await expect(page).toHaveURL('http://localhost:3000/register')

        // 2. Fill the form and request the magic link.
        await page.getByLabel('Email').fill(email)
        await page.getByLabel('Phone').fill(phone)
        await page.getByLabel('Zipcode').fill(zipcode)
        await page.getByRole('button', { name: 'Email me a sign-in link' }).click()
        // SMTP send through Mailpit can take >5s; bump the assertion wait.
        await expect(page.getByText('Check your email.')).toBeVisible({ timeout: 30_000 })

        // 3. Pull the magic link out of Mailpit's API and visit it.
        const magicHref = await fetchMagicLink(email)
        await page.goto(magicHref)
        await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible({ timeout: 30_000 })
      } finally {
        // Closing the context drops the entire session (cookies, storage,
        // both pages). No explicit logout needed — next iteration starts
        // with a fresh context regardless.
        await ctx.close()
      }
    }
  })
})
