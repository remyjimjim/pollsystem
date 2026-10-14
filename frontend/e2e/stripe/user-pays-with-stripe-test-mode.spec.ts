import { test, expect, type Page } from '@playwright/test'
import { clearMailpit, fetchMagicLink } from '../mailpit'
import { BASE, hold, resolveLocation } from '../seed'

// OPT-IN: real Stripe test mode. Skipped unless STRIPE_E2E=1 (CI never sets
// it). Prerequisites (docs/TESTING-E2E.md, "Real Stripe test mode"):
//   - backend on the host with APP_PAYMENTS_PROVIDER=stripe, STRIPE_API_KEY
//     (sk_test_…), STRIPE_PRICE_ID (a test recurring price) and
//     STRIPE_WEBHOOK_SECRET (the whsec_… printed by `stripe listen`);
//   - `stripe listen --forward-to localhost:8080/webhooks/stripe` running.
//
// Actor: a new visitor. Journey: register on our page -> Stripe's hosted
// Checkout -> pay with the 4242 test card -> back to "Payment received" ->
// the checkout.session.completed webhook provisions the account and emails a
// magic link -> sign in -> a paid member (Manage subscription). A second test
// uses the declining card and checks no account (and no email) appears. A
// third opens Stripe's billing portal from Manage subscription, then ends the
// subscription through Stripe's test API (the portal cancels at period end,
// too late for a test) and checks the customer.subscription.deleted webhook
// lapses the membership. It needs STRIPE_API_KEY (test-stripe.bash exports it).
//
// Stripe's Checkout page isn't ours; if its layout changes, the selectors in
// payOnStripe() are the place to adjust.

const CARD_OK = '4242 4242 4242 4242'
const CARD_DECLINED = '4000 0000 0000 0002'

test.skip(!process.env.STRIPE_E2E, 'Real Stripe test mode: set STRIPE_E2E=1 (see docs/TESTING-E2E.md)')

function newVisitor(zipcode: string) {
  const n = Date.now()
  return {
    email: `zzz-stripe-${n}@test.local`,
    phone: `+1555${String(n).slice(-7)}`,
    zipcode,
  }
}

async function startCheckout(page: Page, user: { email: string; phone: string; zipcode: string }) {
  await page.goto(`${BASE}/register`)
  await page.getByLabel('Email').fill(user.email)
  await page.getByLabel('Phone').fill(user.phone)
  await page.getByLabel('Zipcode').fill(user.zipcode)
  await hold(page)
  await page.getByRole('button', { name: /Continue to payment/ }).click()
  // Stripe's hosted Checkout (test mode shows a "TEST MODE" badge).
  await page.waitForURL(/checkout\.stripe\.com/, { timeout: 30_000 })
  await expect(page.locator('#cardNumber')).toBeVisible({ timeout: 30_000 })
}

/** Fill Stripe Checkout's card form and submit. */
async function payOnStripe(page: Page, card: string) {
  // Some accounts show a payment-method chooser first; pick "Card" if so.
  const cardChoice = page.getByTestId('card-accordion-item-button')
  if (await cardChoice.isVisible().catch(() => false)) await cardChoice.click()
  await page.locator('#cardNumber').fill(card)
  await page.locator('#cardExpiry').fill('12 / 34')
  await page.locator('#cardCvc').fill('123')
  await page.locator('#billingName').fill('Test Payer')
  const postal = page.locator('#billingPostalCode')
  if (await postal.isVisible().catch(() => false)) await postal.fill('80401')
  await hold(page)
  await page.getByTestId('hosted-payment-submit-button').click()
}

/** Register, pay with the test card, and sign in through the emailed link. */
async function subscribeAndSignIn(page: Page) {
  const loc = await resolveLocation()
  const user = newVisitor(loc.zipcode)
  await startCheckout(page, user)
  await payOnStripe(page, CARD_OK)

  // Back on our site.
  await page.waitForURL(/checkout=success/, { timeout: 60_000 })
  await expect(page.getByText(/Payment received/)).toBeVisible({ timeout: 15_000 })
  await hold(page)

  // The webhook (via `stripe listen`) provisions the account and emails a sign-in link.
  const href = await fetchMagicLink(user.email, { attempts: 120 })
  await page.goto(href)
  await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible({ timeout: 30_000 })

  // A paid member: the home page offers to manage the subscription. paid_until
  // can land a moment after checkout, so allow a few reloads.
  await expect(async () => {
    await page.goto(`${BASE}/`)
    await expect(page.getByRole('button', { name: 'Manage subscription' })).toBeVisible({ timeout: 3_000 })
  }).toPass({ timeout: 60_000 })
  await hold(page)
  return user
}

/** Stripe's REST API with the TEST key (never logged). */
async function stripeApi<T>(method: string, path: string): Promise<T> {
  const key = process.env.STRIPE_API_KEY ?? ''
  if (!key.startsWith('sk_test_')) throw new Error('STRIPE_API_KEY must be a Stripe TEST key (sk_test_…)')
  const res = await fetch(`https://api.stripe.com/v1/${path}`, { method, headers: { Authorization: `Bearer ${key}` } })
  const body = (await res.json()) as T & { error?: { message?: string } }
  if (!res.ok) throw new Error(`Stripe ${method} ${path}: ${body.error?.message ?? res.status}`)
  return body
}

test.describe('a visitor pays with Stripe test mode', () => {
  test.beforeEach(async () => { await clearMailpit() })

  test('pays with the test card, then signs in as a paid member', async ({ page }) => {
    test.setTimeout(180_000)
    await subscribeAndSignIn(page)
  })

  test('a declined card creates no account', async ({ page }) => {
    test.setTimeout(120_000)
    const loc = await resolveLocation()
    const user = newVisitor(loc.zipcode)

    await startCheckout(page, user)
    await payOnStripe(page, CARD_DECLINED)

    // Stripe keeps us on its page with a decline message.
    await expect(page.getByText(/declined/i).first()).toBeVisible({ timeout: 30_000 })
    await expect(page).toHaveURL(/checkout\.stripe\.com/)
    await hold(page)

    // No webhook-provisioned account, so no sign-in email.
    await expect(fetchMagicLink(user.email, { attempts: 10 })).rejects.toThrow(/No magic link/)
  })

  test('Manage subscription opens Stripe\'s portal; when the subscription ends, the membership lapses', async ({ page }) => {
    test.setTimeout(240_000)
    const user = await subscribeAndSignIn(page)

    // 1. Manage subscription -> Stripe's billing portal, showing the subscription.
    //    (A test-mode portal must be set up once in the Stripe dashboard:
    //    Settings -> Billing -> Customer portal -> Save.)
    await page.getByRole('button', { name: 'Manage subscription' }).click()
    await page.waitForURL(/billing\.stripe\.com/, { timeout: 30_000 })
      .catch(() => { throw new Error('Manage subscription did not reach Stripe\'s portal. Is the test-mode Customer portal saved in the Stripe dashboard?') })
    await expect(page.getByText(/\$10\.00/).first()).toBeVisible({ timeout: 30_000 })
    await hold(page)

    // 2. End the subscription now, through Stripe's test API.
    const customers = await stripeApi<{ data: Array<{ id: string }> }>('GET', `customers?email=${encodeURIComponent(user.email)}`)
    expect(customers.data.length, 'a Stripe customer for the new member').toBeGreaterThan(0)
    const subs = await stripeApi<{ data: Array<{ id: string }> }>('GET', `subscriptions?customer=${customers.data[0].id}&status=active`)
    expect(subs.data.length, 'an active subscription').toBe(1)
    await stripeApi('DELETE', `subscriptions/${subs.data[0].id}`)

    // 3. The customer.subscription.deleted webhook lapses the membership.
    await expect(async () => {
      await page.goto(`${BASE}/`)
      await expect(page.getByText('Your membership has lapsed')).toBeVisible({ timeout: 3_000 })
    }).toPass({ timeout: 60_000 })
    await expect(page.getByRole('button', { name: /Renew/ })).toBeVisible()
    await hold(page)
  })
})
