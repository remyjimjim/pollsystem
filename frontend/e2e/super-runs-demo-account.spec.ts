import { test, expect } from '@playwright/test'
import { BASE, hold, seedUser, devToken } from './seed'

// Actors: super + a prospect (decided 2026-10-28). The super switches on the
// shared demo address from the Super dashboard; the prospect types it on Sign
// in and is straight in (no email, code or payment), with a notice saying when
// it ends; End now deletes it and the prospect's next page signs them out.
// Leaves the demo off.
const DEMO = 'demo@test.local'

test.describe('super runs the demo account', () => {
  let superEmail = ''

  test.beforeAll(async () => {
    superEmail = (await seedUser({ access: 'SUPER', zipcode: '98225' })).email
  })

  test('start, prospect signs straight in, end, prospect is signed out', async ({ browser }) => {
    test.setTimeout(180_000)
    const superCtx = await browser.newContext()
    await superCtx.addInitScript(t => localStorage.setItem('token', t), await devToken(superEmail))
    const sup = await superCtx.newPage()
    await sup.goto(`${BASE}/super/dashboard`)
    const card = sup.locator('[data-test="demo-card"]')
    await expect(card).toContainText(DEMO)
    // A demo left on by an earlier run: end it first.
    if (await card.locator('[data-test="demo-end"]').isVisible()) {
      sup.once('dialog', d => d.accept())
      await card.locator('[data-test="demo-end"]').click()
    }
    await card.locator('[data-test="demo-zipcode"]').fill('98225')
    await card.locator('[data-test="demo-start"]').click()
    await expect(card.locator('[data-test="demo-status"]')).toContainText('On until')
    await expect(card.locator('[data-test="demo-status"]')).toContainText('98225')
    await hold(sup)

    const prospect = await (await browser.newContext({ viewport: { width: 390, height: 844 } })).newPage()
    await prospect.goto(`${BASE}/login`)
    await prospect.getByLabel('Email').fill(DEMO)
    await prospect.getByRole('button', { name: 'Email me a sign-in link' }).click()
    await expect(prospect).not.toHaveURL(/\/login/, { timeout: 15_000 })
    await expect(prospect.locator('[data-test="demo-notice"]')).toContainText('demo account')
    await expect(prospect.getByRole('button', { name: 'Logout' })).toBeVisible()
    await hold(prospect)

    await card.locator('[data-test="demo-extend"]').click()
    await expect(card.locator('[data-test="demo-status"]')).toContainText('On until')

    sup.once('dialog', d => d.accept())
    await card.locator('[data-test="demo-end"]').click()
    await expect(card.locator('[data-test="demo-status"]')).toHaveText('Off')

    await prospect.goto(`${BASE}/`)
    await expect(prospect.getByRole('link', { name: 'Login' }).first()).toBeVisible({ timeout: 15_000 })
    await expect(prospect.locator('[data-test="demo-notice"]')).toHaveCount(0)
    await hold(prospect)
  })
})
