import { test, expect } from '@playwright/test'

test.describe('register Colorado users via magic link', () => {
  test('register and log in 10 users in isolated browser contexts', async ({ browser }) => {
    // Ten Mailpit round-trips at ~5s each + form fills; give it room.
    test.setTimeout(180_000)

    for (let i = 1; i <= 10; i++) {
      const email   = `zzz${i}testuser@colorado.com`
      const phone   = String(3031111110 + i)
      const zipcode = String(80001 + i)

      // Each iteration gets its own browser context = isolated cookies,
      // localStorage, sessionStorage. This is what makes the sessions
      // truly separate; using browser.newPage() alone would share storage
      // and one iteration's login would bleed into the next.
      const ctx = await browser.newContext()
      const page = await ctx.newPage()

      try {
        // 1. Home → Register
        await page.goto('http://localhost:3000')
        await page.getByRole('link', { name: 'Register' }).click()
        await expect(page).toHaveURL('http://localhost:3000/register')

        // 2. Fill the form and request the magic link.
        await page.getByLabel('Email').fill(email)
        await page.getByLabel('Phone').fill(phone)
        await page.getByLabel('Zipcode').fill(zipcode)
        await page.getByRole('button', { name: 'Email me a sign-in link' }).click()
        await expect(page.getByText('Check your email.')).toBeVisible()

        // 3. Mailpit: open the message addressed to this iteration's user.
        //    Filtering by recipient instead of blindly clicking "top"
        //    avoids races if previous iterations' emails are still settling.
        const mail = await ctx.newPage()
        await mail.goto('http://localhost:8025')
        await mail.locator('.message-list .message', { hasText: email })
          .first().click()
        await expect(mail.getByText('Your sign-in link')).toBeVisible()

        // 4. Pull the magic link out of the rendered email body iframe.
        const magicHref = await mail.frameLocator('iframe')
          .locator('a[href^="http://localhost:3000/auth/magic-link?token="]')
          .first()
          .getAttribute('href')
        expect(magicHref, `no magic link found for ${email}`).not.toBeNull()
        await mail.close()

        // 5. Visit the magic link in the original page → user is signed in.
        await page.goto(magicHref!)
        await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible()
      } finally {
        // Closing the context drops the entire session (cookies, storage,
        // both pages). No explicit logout needed — next iteration starts
        // with a fresh context regardless.
        await ctx.close()
      }
    }
  })
})
