package org.kodewerks.pollsystem.stripe

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.stripe")
data class StripeProperties(
    /** Endpoint signing secret from the Stripe dashboard, e.g. `whsec_…`. */
    val webhookSecret: String = "",
    /** How old (in seconds) a signed timestamp may be before we reject it. */
    val toleranceSeconds: Long = 300,
    /**
     * Secret or restricted API key (`sk_…` / `rk_…`), used only to create
     * Checkout + Customer Portal sessions. Blank ⇒ the billing endpoints
     * return 503 (the webhook path is unaffected — it needs no key).
     */
    val apiKey: String = "",
    /** Price id of the Creator subscription (`price_…`). Blank ⇒ checkout 503s. */
    val priceId: String = "",
    /**
     * Frontend paths Stripe redirects back to, appended to
     * `app.magic-link.base-url`. `{CHECKOUT_SESSION_ID}` is substituted by Stripe.
     */
    val checkoutSuccessPath: String = "/?checkout=success&session_id={CHECKOUT_SESSION_ID}",
    val checkoutCancelPath: String = "/?checkout=cancel",
    val portalReturnPath: String = "/"
)
