package org.kodewerks.pollsystem.payment

import org.kodewerks.pollsystem.model.User

/**
 * Provider-agnostic payment operations the app invokes (outbound). Everything
 * downstream — the paid_until participation gate, VIEWER↔USER demote/promote,
 * creator-discount rules — depends only on this interface, not on Stripe.
 *
 * Swapping processors (see docs/payment-processors.md) means adding a new
 * implementation of this interface plus a matching inbound webhook adapter
 * (the current Stripe adapter is `stripe/StripeWebhookController` +
 * `StripeWebhookService`); the access model and business logic don't change.
 */
interface PaymentProvider {
    /** Start a subscription purchase for [user]; returns a hosted-page URL to redirect to. */
    fun createCheckoutSession(user: User): String

    /**
     * Start a subscription purchase for a not-yet-registered visitor (pay-first
     * registration). No account exists yet: [email] seeds the hosted checkout,
     * and [phone] + [zipcode] ride along in the session metadata so the inbound
     * webhook can provision a *complete* account once payment succeeds. Returns
     * a hosted-page URL to redirect to.
     */
    fun createGuestCheckoutSession(email: String, phone: String, zipcode: String): String

    /** Open the self-service billing portal for [user]; returns a hosted-page URL. */
    fun createPortalSession(user: User): String

    /** Apply the creator discount to [user]'s subscription. Best-effort; no-op if unconfigured. */
    fun applyCreatorDiscount(user: User)

    /** Remove any discount from [user]'s subscription. Best-effort. */
    fun removeCreatorDiscount(user: User)
}
