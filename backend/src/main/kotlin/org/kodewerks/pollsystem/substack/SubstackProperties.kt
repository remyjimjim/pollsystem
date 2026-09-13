package org.kodewerks.pollsystem.substack

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Config for the inbound Substack membership webhook. Substack bills members
 * externally (not through Stripe), so a subscribe/renewal event grants a rolling
 * window of membership rather than tracking a Stripe subscription.
 */
@ConfigurationProperties(prefix = "app.substack")
data class SubstackProperties(
    /**
     * Shared secret Substack (or a relay such as Zapier) sends in the
     * `X-Webhook-Secret` header. Blank ⇒ the endpoint returns 503 (disabled),
     * so an unconfigured deployment can't be driven by forged calls.
     */
    val webhookSecret: String = "",
    /**
     * Days of membership each subscribe/renewal event grants (option A: rolling
     * window). Each event pushes `paid_until` to now + this many days; an
     * unsubscribe event drops it. Sized a little over a month so a monthly
     * renewal event keeps membership continuous.
     */
    val membershipDays: Long = 32
)
