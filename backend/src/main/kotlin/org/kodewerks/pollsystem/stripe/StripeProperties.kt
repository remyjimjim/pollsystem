package com.pollsystem.stripe

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.stripe")
data class StripeProperties(
    /** Endpoint signing secret from the Stripe dashboard, e.g. `whsec_…`. */
    val webhookSecret: String = "",
    /** How old (in seconds) a signed timestamp may be before we reject it. */
    val toleranceSeconds: Long = 300
)
