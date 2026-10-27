# Stripe: live mode (prod)

Set up 2026-10-22. Staging keeps using the Stripe **sandbox** (test mode).
Staging's sandbox price since 2026-10-27: `price_1UQZQGLEdI7bvvoxsM2qz3Bf`, $4.99 / month.
Nothing here is secret; the keys and the webhook signing secret live only in
Fly secrets and in git-ignored files under `.devcontainer/`.

| What | Value |
|---|---|
| Account | `acct_1UDCi7LEdI7bvvox` (World Wide Wakeup, LLC) |
| Product | `prod_VW28cHNQTjuV4m`, "BallotBox" (since 2026-10-27) |
| Price | `price_1UV03sLEdI7bvvox8KDo3uBh`, $4.99 / month (since 2026-10-27), no lookup key |
| Former | product `prod_VUBXEqb9F8XUqA` "BallotBox Membership" (archived 2026-10-27) with price `price_1UTDAuLEdI7bvvoxNtjBC6XD`, $10.00 / month, lookup key `ballotbox_membership_monthly`; subscriptions started on it stay at $10 until moved |
| Webhook | `we_1UTDAuLEdI7bvvoxH44iF8rz` → `https://pollsystem-backend.fly.dev/webhooks/stripe` |
| Webhook events | `checkout.session.completed`, `customer.subscription.created`, `.updated`, `.deleted`, `invoice.paid`, `invoice.payment_failed` |
| Customer portal | `bpc_1UTCTeLEdI7bvvoxcrUqIQxf` (default): cancel **at end of billing period**, update payment methods, invoice history; links to `/legal/terms` and `/legal/privacy`; returns to `https://ballotbox.us.com/` |
| Statement descriptor | `BALLOTBOX.US.COM` |

## Prod's Fly secrets

`STRIPE_API_KEY` (the live secret key), `STRIPE_PRICE_ID` and
`STRIPE_WEBHOOK_SECRET`, set from `.devcontainer/stripe-live-app.secrets.env`
with `flyctl secrets import -a pollsystem-backend < …`. The live key itself is in
`.devcontainer/stripe-live.secrets.env`. Both files are readable only by you.

## Changing things

- **Rotating the key:** Stripe → Developers → API keys. Update both files,
  re-import to Fly, then expire the old key.
- **A new price:** create it in Stripe, set `STRIPE_PRICE_ID`. Existing
  subscribers stay on their price until moved.
- **The webhook signing secret** is shown only once, when the endpoint is
  created, or later via Stripe → Developers → Webhooks → the endpoint → Reveal.
- **Checking deliveries:** Stripe → Developers → Webhooks → the endpoint shows
  each event and the backend's answer (200 = handled).
