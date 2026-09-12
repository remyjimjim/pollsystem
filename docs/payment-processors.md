# Payment processors — options & Stripe fallback

**Why this doc:** we run on **Stripe** and are happy with it. This is
vendor-risk insurance — if Stripe's pricing or terms ever turned unfavorable,
this catalogs the realistic alternatives so a switch is a *known, contained*
move rather than a research scramble. The `PaymentProvider` abstraction (see the
bottom) is what keeps that switch cheap.

> TL;DR — Stay on Stripe. Like-for-like Plan B: **Braintree**. Offload-tax Plan
> B: **Paddle** (merchant of record). Of the old-guard gateways, only
> **Authorize.Net** is still a real choice.

---

## The legacy names (mostly history)

The question "how did sites charge cards before PayPal?" points at these — a
merchant account plus a **gateway**. Most are gone or folded in:

- **CyberCash** (1994) — one of the first internet card gateways. Went bankrupt
  in 2001; assets went to VeriSign. **Defunct** — historical interest only.
- **VeriSign Payflow** — VeriSign's gateway (Payflow Pro/Link). VeriSign sold its
  payments business to **PayPal in 2005**. It survives as *PayPal Payflow*, but
  it's a **legacy** product PayPal has been winding down. Don't build new on it.
- **Authorize.Net** (1996) — the survivor. Now owned by **Visa** (via
  CyberSource). Still widely used, especially by US businesses. Classically a
  **gateway** paired with a *separate* merchant account, though it also sells an
  all-in-one. Established and dependable, but an older developer experience than
  Stripe/Braintree.

**Takeaway:** of the old guard, only **Authorize.Net** is a viable option today.

---

## Comparison (the viable options)

Pricing is US card-present-online, **as of 2026 — verify before relying on it**;
processor pricing pages change.

| Processor | Status / Owner | Model | Recurring / subscriptions | Sales tax / VAT | Java (JVM) SDK | ~Pricing (US cards) | As a Stripe Plan B |
|---|---|---|---|---|---|---|---|
| **Stripe** *(current)* | Active · independent | Full processor; you are merchant of record | Native (Billing) | Stripe Tax **calculates**; **you remit** | Yes (`stripe-java`) | 2.9% + $0.30 | — |
| **Braintree** | Active · **PayPal** | Full processor; you are MoR | Native | You remit (no built-in tax) | Yes (official Java SDK) | ~2.9% + $0.30 | ★ **Best like-for-like.** Independent of Stripe, cards + PayPal + Venmo, mature API. |
| **Paddle** | Active · independent (UK) | **Merchant of Record** — Paddle is the seller | Native | **Paddle remits** globally (VAT/sales tax handled for you) | Via REST API (no first-party Java SDK; thin HTTP client) | ~5% + $0.50 (bundles tax/compliance) | ★ **Best if you want tax off your plate.** Higher fee buys compliance. |
| **Square** | Active · **Block, Inc.** | Full processor; you are MoR | Native (Subscriptions API) | You remit | Yes (official Java SDK) | 2.9% + $0.30 online | Solid, independent; strongest at in-person/omnichannel. |
| **Authorize.Net** | Active · **Visa** | Gateway (+ merchant account) or all-in-one | Yes (ARB — Automated Recurring Billing) | You remit | Yes (official Java SDK, older style) | Gateway ~$25/mo + ~$0.10/txn **plus** merchant-account rates; or all-in-one ~2.9% + $0.30 | Works, but older DX and the merchant-account split adds setup. US-centric. |
| ~~Lemon Squeezy~~ | Active · **Stripe** (acq. 2024) | Merchant of Record | Native | MoR remits | REST | ~5% + $0.50 | **Skip for redundancy** — it's Stripe-owned, so it doesn't reduce Stripe dependence. |
| ~~CyberCash~~ | **Defunct** (2001) | Gateway | — | — | — | — | Not an option. |
| ~~VeriSign Payflow~~ | **Legacy** · PayPal | Gateway | Yes | You remit | Legacy | Legacy | Not for new builds. |

---

## What actually matters for *this* app

pollsystem is a Spring Boot / Kotlin subscription SaaS run by a small team. The
decision axes that matter:

1. **Native recurring subscriptions** — all viable options have this.
2. **Payouts to your bank** — every real processor does this (it's the whole
   point); "deposits to my bank" is not a differentiator, it's table stakes.
3. **PCI-light (tokenization / hosted fields)** — never touch raw card numbers.
   All modern options provide this; it's the reason we don't store cards.
4. **A first-party JVM/Java SDK** — Stripe, Braintree, Square, and
   Authorize.Net have one; Paddle is REST-only (a thin HTTP client is fine).
5. **Who remits sales tax/VAT** — the big model split:
   - **You are the merchant of record** (Stripe, Braintree, Square,
     Authorize.Net): *you* are responsible for collecting and filing tax. Stripe
     Tax and similar *calculate* it, but you still register and remit.
   - **They are the merchant of record** (Paddle, Lemon Squeezy): they handle
     tax globally as the seller and pay you a net amount. Higher fee, far less
     compliance burden.

---

## Recommendation / Plan B

- **Stay on Stripe.** It's large, stable, and its 2.9% + $0.30 pricing has held
  for years; ToS/pricing changes tend to be incremental. The odds of *needing*
  to leave are low.
- **Like-for-like fallback → Braintree.** Same integration shape (you're MoR,
  first-party Java SDK, cards + wallets, native subscriptions, payouts), and
  it's owned by PayPal — genuinely independent of Stripe.
- **Offload-tax fallback → Paddle.** If the pain point ever becomes *tax and
  compliance* rather than Stripe itself, a merchant-of-record moves that burden
  off you entirely — worth the higher fee.
- **Don't pick Lemon Squeezy for independence** — Stripe acquired it in 2024.

---

## Migration path (why the switch is cheap)

Stripe touches a small, well-contained surface of the app:

- `stripe/BillingService.kt` — creates Checkout + Customer Portal sessions and
  applies/removes the creator discount.
- `stripe/StripeWebhookService.kt` + `StripeWebhookController.kt` — verifies and
  applies webhook events (sets `paid_until`, promotes/demotes access).
- Three columns on `users`: `stripe_customer_id`, `stripe_subscription_id`,
  `paid_until`.

Everything downstream — the `paid_until` participation gate, the
VIEWER↔USER demote/promote, the creator discount rules — is **provider-agnostic**.

A switch therefore means: implement the new provider behind the **`PaymentProvider`
abstraction** (checkout/portal/discount + a webhook adapter), re-map those three
user columns to the new provider's ids, and repoint config. The access model and
all business logic stay untouched. Keeping that boundary clean — not running two
processors at once — is the redundancy investment that actually pays off for a
small team.
