# Stripe test-mode runbook — verify the paid path before going live

This runbook verifies the **revenue path end to end in Stripe _test_ mode**:

> pay via Stripe Checkout → webhook provisions a paid account → magic-link email →
> sign in → complete profile → participate, with `paid_until` set.

No real money moves in test mode. Run **Part A** (local) during development, and
**Part B** (deployed, test-mode-first) as the go-live gate — *only flip to live
keys after Part B passes.*

## What the backend does with each event

`POST /webhooks/stripe` verifies the `Stripe-Signature` header against
`STRIPE_WEBHOOK_SECRET` (5-minute tolerance), dedupes by event id, then:

| Event | Effect (see `StripeWebhookService`) |
|---|---|
| `checkout.session.completed` | Email **unknown** → provision a paid user (email only, `access=USER`, phone/zipcode null) **and email a magic link**. Email **known** → link Stripe customer + subscription ids to the existing user. |
| `customer.subscription.updated` / `invoice.paid` | Refresh `paid_until` from the period end (found by `stripe_subscription_id`). |
| `customer.subscription.deleted` | Clear `paid_until` + `stripe_subscription_id` (access revoked). |
| `invoice.payment_failed` | Logged only — access drops on `subscription.deleted`. |

Note: `checkout.session.completed` sets the Stripe ids; **`paid_until` arrives on
the following `subscription.updated` / `invoice.paid`**, so a fresh subscriber
becomes "paid" a beat after checkout.

## Prerequisites

- A Stripe account in **Test mode** (toggle top-right of the Stripe dashboard).
- Test API keys: `sk_test_…` (secret) and a test **webhook signing secret**
  `whsec_…` (from `stripe listen` in Part A, or a test webhook endpoint in Part B).
- The [Stripe CLI](https://stripe.com/docs/stripe-cli): `stripe login`.
- A test **Product + recurring Price** and a **Payment Link** (or Checkout
  Session) in test mode pointing at that price.
- Test card: **`4242 4242 4242 4242`**, any future expiry, any CVC, any ZIP.
  (Decline test: `4000 0000 0000 0002`.)

---

## Part A — Local verification (Stripe CLI → localhost)

1. **Start the local stack** (see `docs/DEPLOYING-LOCAL.md`): Postgres, Mailpit,
   backend on the `local` profile (`SPRING_PROFILES_ACTIVE=local`), frontend
   (`npm run dev`). Magic-link emails land in **Mailpit** (`http://localhost:8025`).

2. **Forward webhooks to the backend** and grab the signing secret:
   ```bash
   stripe listen --forward-to localhost:8080/webhooks/stripe
   # prints:  Ready! Your webhook signing secret is whsec_xxx
   ```

3. **Point the backend at that secret** and restart it:
   ```bash
   export STRIPE_WEBHOOK_SECRET=whsec_xxx   # maps to app.stripe.webhook-secret
   ```
   (Locally you can also put it in `application-local.yml` under
   `app.stripe.webhook-secret`.)

4. **Run a test checkout** against an email that has **never registered** — e.g.
   open your test Payment Link, pay with `4242…`. (Or simulate directly:
   `stripe trigger checkout.session.completed`.)

5. **Verify provisioning:**
   - The `stripe listen` terminal shows `checkout.session.completed` → `200`.
   - **Mailpit** shows a "Your sign-in link" email to the checkout address.
   - DB check:
     ```
     PGPASSWORD=pollpass123 psql -h localhost -U polladmin -d pollsystem \
       -c "select email, access, phone, zipcode, stripe_customer_id, paid_until from users order by id desc limit 3;"
     ```
     The new row has a `stripe_customer_id`, `access = USER`, and **null phone/zipcode**.

6. **Refresh `paid_until`:** `stripe trigger customer.subscription.updated`
   (or `invoice.paid`). Re-check the DB — `paid_until` is now set in the future.

7. **Finish onboarding in the browser:** click the magic link from Mailpit →
   you land on **/complete-profile** → enter phone + zipcode → you can now search
   and complete a poll. This confirms the full Phase-2→Phase-4 chain.

8. **Revocation (optional):** `stripe trigger customer.subscription.deleted` →
   `paid_until` clears.

**Troubleshooting**
- `400 invalid signature` → the backend's `STRIPE_WEBHOOK_SECRET` doesn't match
  the `whsec_` from `stripe listen`. Re-copy it and restart.
- No new user, log says *"matches no existing user; ignoring"* → that's the
  **old** behavior; confirm you're on the provisioning build (Phase 2).
- No email in Mailpit → check the backend is on the `local` profile (Mailpit
  sender) and Mailpit is up on `:1025`/`:8025`.

---

## Part B — Test-mode-first on the deployed environment (go-live gate)

Do this once against the real deployment **before** switching to live keys.

1. **Deploy the backend with TEST keys:**
   ```bash
   flyctl secrets set -a pollsystem-backend \
     STRIPE_SECRET_KEY=sk_test_xxx \
     STRIPE_WEBHOOK_SECRET=whsec_test_endpoint_xxx \
     APP_BASE_URL=https://codehutch.org
   ```
   `APP_BASE_URL` must be the **frontend** origin so magic links resolve to the SPA.

2. **Create a TEST webhook endpoint** in Stripe pointing at the backend
   (this is the same command from `DEPLOYING-FLY.md` Part 3, in test mode):
   ```bash
   stripe webhooks create \
     --url https://pollsystem-backend.fly.dev/webhooks/stripe \
     --enabled-events checkout.session.completed \
     --enabled-events customer.subscription.updated \
     --enabled-events customer.subscription.deleted \
     --enabled-events invoice.paid \
     --enabled-events invoice.payment_failed
   ```
   Use the returned `whsec_…` as `STRIPE_WEBHOOK_SECRET` above.

3. **Run a real test checkout** against the deployed Payment Link with the `4242`
   card and a fresh email. The magic-link email now goes out via the **production
   email provider** (Resend/SES) — confirm it actually lands (deliverability is
   the thing local Mailpit can't prove).

4. **Verify** the same chain as Part A: provisioned paid user, magic link →
   complete-profile → participate, `paid_until` set. Check `flyctl logs`.

5. **Only now, go live:** swap to `sk_live_…`, create a **live** webhook endpoint
   + its `whsec_…`, and point a **live** Payment Link at it. Live and test data
   are separate in Stripe, so re-verify one real (small) transaction if you can.

## Reference

- Test cards: https://stripe.com/docs/testing
- Stripe CLI triggers: `stripe trigger --help`
- Webhook signing + tolerance: `app.stripe.webhook-secret`, `app.stripe.tolerance-seconds` (300)
