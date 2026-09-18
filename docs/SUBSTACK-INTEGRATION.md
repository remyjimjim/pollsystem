# Substack → pollsystem membership integration

How a paid Substack subscriber becomes a paid pollsystem member. Substack has
**no native "new subscriber → your webhook URL"** feature, so a **relay**
(Zapier / Make / n8n) sits in the middle and calls our generic webhook. Our side
(`substack/SubstackWebhookController` + `SubstackWebhookService`) doesn't care who
calls it — it just needs the right JSON and a shared-secret header.

> Verify Substack's current capabilities in your account (Settings, and the
> Zapier/Make app catalog) — they change. As of writing, a relay is required.

---

## Our webhook — the contract

**Endpoint:** `POST /webhooks/substack`
- Staging: `https://pollsystem-backend-staging.fly.dev/webhooks/substack`
- Prod: `https://<prod-backend-host>/webhooks/substack`

**Auth:** header `X-Webhook-Secret: <secret>`, matched (constant-time) against the
`SUBSTACK_WEBHOOK_SECRET` Fly secret. **Blank/unset ⇒ the endpoint is disabled
and returns 503**, so it can't be driven before you configure it.

**Body:** `{"email": "...", "event": "..."}`

**Event → action** (case-insensitive; `SubstackWebhookController`):
| Effect | Accepted `event` values |
|---|---|
| **Activate** (provision/renew a paid member) | `subscribed`, `subscription.created`, `renewed`, `renewal`, `invoice.paid`, `active` |
| **Deactivate** (demote to VIEWER) | `unsubscribed`, `subscription.deleted`, `canceled`, `cancelled`, `inactive`, `expired` |
| Ignored (acked, no change) | anything else |

**Responses:** `200 ok` (applied) · `200 ignored` (unhandled event) ·
`401` (bad/missing secret) · `503` (secret not configured) · `400` (malformed email).

**What activate does** (`SubstackWebhookService`):
- Unknown email → provisions a `USER` with `paid_until = now + app.substack.membership-days`
  (default **32**), **email only** (phone + zipcode collected via "complete your
  profile" at first sign-in), and emails a magic sign-in link (Resend in staging/prod).
- Existing email → extends `paid_until`; restores a lapsed `VIEWER` to `USER`.

**What deactivate does:** clears `paid_until` and demotes a non-`SUPER` account to
`VIEWER` (same as the Stripe cancel path).

> **Rolling window (option A):** each activate grants ~32 days. Substack bills
> externally, so if you want continuous membership the relay must also fire on
> **renewals** (map any renewal/renewed/invoice event to an activate), or bump
> `app.substack.membership-days`. Otherwise a member lapses ~32 days after the
> last event.

---

## Step 1 — set the shared secret

Pick a strong random value and set it as a Fly secret (this redeploys the app):

```bash
# staging
flyctl secrets set SUBSTACK_WEBHOOK_SECRET="$(openssl rand -hex 24)" -a pollsystem-backend-staging
```

Fly secrets are write-only, so **note the value** where the relay can use it (a
password manager, or the OS keychain like the other staging secrets:
`secret-tool store --label='staging SUBSTACK_WEBHOOK_SECRET' service pollsystem-fly-staging account SUBSTACK_WEBHOOK_SECRET`).

---

## Step 2 — the relay (Zapier example)

1. **Trigger:** Substack → *New Subscriber* (verify the exact trigger name in your
   Zapier account; Make/n8n have equivalents).
2. **Action:** *Webhooks by Zapier → Custom Request*
   - **Method:** `POST`
   - **URL:** `https://pollsystem-backend-staging.fly.dev/webhooks/substack`
   - **Headers:**
     - `X-Webhook-Secret: <the value from step 1>`
     - `Content-Type: application/json`
   - **Data (JSON):** `{"email": "{{subscriber_email}}", "event": "subscribed"}`
     (map `{{subscriber_email}}` to the trigger's email field)
3. **Cancellations (recommended):** a second zap on Substack's cancel/churn trigger
   → same request with `"event": "unsubscribed"` → demotes the member.

---

## Step 3 — generate a test event on Substack

In your Substack dashboard you can **manually add a subscriber** by email, or
**comp / gift a paid subscription** to a test address — either fires the "new
subscriber" trigger, so the zap POSTs to our endpoint and the member is provisioned.
Then check `/api/auth/status` (below) or sign in via the magic-link email.

---

## Testing without Substack (simulate the relay)

The endpoint is generic, so you can POST exactly what the relay would — no Substack
or Zapier needed:

```bash
S=<your SUBSTACK_WEBHOOK_SECRET>
BASE=https://pollsystem-backend-staging.fly.dev

# new paid subscriber → provisions a member + emails a magic link
curl -X POST "$BASE/webhooks/substack" -H "X-Webhook-Secret: $S" \
  -H 'Content-Type: application/json' -d '{"email":"you+sub@example.com","event":"subscribed"}'

# check it: UNKNOWN → ACTIVE
curl -X POST "$BASE/api/auth/status" -H 'Content-Type: application/json' \
  -d '{"email":"you+sub@example.com"}'      # {"status":"ACTIVE"}

# cancellation → demote
curl -X POST "$BASE/webhooks/substack" -H "X-Webhook-Secret: $S" \
  -H 'Content-Type: application/json' -d '{"email":"you+sub@example.com","event":"unsubscribed"}'
# status now {"status":"LAPSED"}
```

Use a **real** inbox (e.g. a `+alias`) if you want to click the magic link and
finish sign-in; a fake address still provisions the account (the email just isn't
deliverable).

---

## Going to production

Same recipe against the **prod** backend URL and a **prod** `SUBSTACK_WEBHOOK_SECRET`
(set on the prod Fly app), pointing the relay at the prod endpoint. Keep the
staging and prod secrets different.
