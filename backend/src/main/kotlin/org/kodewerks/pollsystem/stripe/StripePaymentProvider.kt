package org.kodewerks.pollsystem.stripe

import com.fasterxml.jackson.databind.ObjectMapper
import com.stripe.StripeClient
import com.stripe.exception.StripeException
import com.stripe.param.SubscriptionUpdateParams
import com.stripe.param.common.EmptyParam
import com.stripe.param.billingportal.SessionCreateParams as PortalSessionCreateParams
import com.stripe.param.checkout.SessionCreateParams
import org.kodewerks.pollsystem.auth.MagicLinkProperties
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.payment.PaymentProvider
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/**
 * Creates Stripe-hosted Checkout and Customer Portal sessions for the Creator
 * subscription. This is the ONE place the app calls Stripe's API — everything
 * else (the webhook) is keyless — so it's gated behind `app.stripe.api-key`
 * and returns 503 until that (and the price id) are configured.
 *
 * Checkout uses `client_reference_id = user.id` so the webhook can link the
 * resulting subscription to this exact user rather than matching by email.
 *
 * The Stripe implementation of [PaymentProvider]; see that interface and
 * docs/payment-processors.md for the path to swapping processors.
 */
@Service
class StripePaymentProvider(
    private val props: StripeProperties,
    private val magicLink: MagicLinkProperties,
    private val objectMapper: ObjectMapper
) : PaymentProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    // Redirect back to the same frontend the magic-link emails point at.
    private val baseUrl: String get() = magicLink.baseUrl.trimEnd('/')

    private fun client(): StripeClient {
        if (props.apiKey.isBlank()) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Billing is not configured")
        }
        return StripeClient(props.apiKey)
    }

    /** Create a subscription Checkout Session for [user]; returns the redirect URL. */
    override fun createCheckoutSession(user: User): String {
        if (props.priceId.isBlank()) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Billing price is not configured")
        }
        val params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(props.priceId)
                    .setQuantity(1L)
                    .build()
            )
            .setSuccessUrl("$baseUrl${props.checkoutSuccessPath}")
            .setCancelUrl("$baseUrl${props.checkoutCancelPath}")
            .setClientReferenceId(user.id.toString())
            .apply {
                // Reuse the Stripe Customer if we already have one; otherwise seed
                // the email so Checkout creates one (captured back in the webhook).
                if (!user.stripeCustomerId.isNullOrBlank()) {
                    setCustomer(user.stripeCustomerId)
                } else {
                    setCustomerEmail(user.email)
                }
            }
            .build()

        return try {
            client().checkout().sessions().create(params).url
        } catch (e: StripeException) {
            log.error("Stripe checkout session creation failed for user={}", user.id, e)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not start checkout")
        }
    }

    /**
     * Create a subscription Checkout Session for a pay-first visitor with no
     * account yet. Seeds `customer_email` and stashes the pre-collected phone +
     * zipcode in the session metadata; the webhook reads them back (keys in
     * [StripeMetadataKeys]) to provision a complete account. No
     * `client_reference_id` — the webhook matches the resulting checkout by email.
     */
    override fun createGuestCheckoutSession(email: String, phone: String, zipcode: String): String {
        if (props.priceId.isBlank()) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Billing price is not configured")
        }
        val params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(props.priceId)
                    .setQuantity(1L)
                    .build()
            )
            .setSuccessUrl("$baseUrl${props.checkoutSuccessPath}")
            .setCancelUrl("$baseUrl${props.checkoutCancelPath}")
            .setCustomerEmail(email)
            .putMetadata(StripeMetadataKeys.PHONE, phone)
            .putMetadata(StripeMetadataKeys.ZIPCODE, zipcode)
            .build()

        return try {
            client().checkout().sessions().create(params).url
        } catch (e: StripeException) {
            log.error("Stripe guest checkout session creation failed for email={}", email, e)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not start checkout")
        }
    }

    /** Create a Customer Portal session for [user]; returns the redirect URL. */
    override fun createPortalSession(user: User): String {
        val customerId = user.stripeCustomerId
        if (customerId.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "No billing account yet — subscribe first")
        }
        val params = PortalSessionCreateParams.builder()
            .setCustomer(customerId)
            .setReturnUrl("$baseUrl${props.portalReturnPath}")
            .build()
        return try {
            client().billingPortal().sessions().create(params).url
        } catch (e: StripeException) {
            log.error("Stripe portal session creation failed for user={}", user.id, e)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not open billing portal")
        }
    }

    /**
     * Apply the creator discount coupon to the user's subscription (an incentive
     * for the create/moderate work creators contribute). No-op if the coupon or
     * API key isn't configured, or the user has no subscription. **Best-effort**:
     * a Stripe failure is logged, not thrown, so it never rolls back the role
     * grant that triggered it.
     */
    override fun applyCreatorDiscount(user: User) = updateSubscriptionDiscount(user, apply = true)

    /** Remove any discount from the user's subscription (best-effort; see above). */
    override fun removeCreatorDiscount(user: User) = updateSubscriptionDiscount(user, apply = false)

    /**
     * Retrieve a subscription's current period end (when the paid access lapses),
     * used by the webhook to set `paid_until` right at checkout completion — so
     * activation doesn't depend on the ordering or delivery of the separate
     * subscription/invoice events. Returns null if billing is unconfigured, the
     * id is blank, or the lookup fails (the caller then falls back to those
     * events). Parses the raw API JSON so it's robust to the SDK's field shape
     * moving `current_period_end` onto subscription items in 2025 API versions.
     */
    fun currentPeriodEnd(subscriptionId: String): Instant? {
        if (props.apiKey.isBlank() || subscriptionId.isBlank()) return null
        return try {
            val sub = client().subscriptions().retrieve(subscriptionId)
            val raw = sub.lastResponse?.body() ?: return null
            val node = objectMapper.readTree(raw)
            val epoch = node.path("current_period_end").asLong(0).takeIf { it > 0 }
                ?: node.path("items").path("data").firstOrNull()
                    ?.path("current_period_end")?.asLong(0) ?: 0L
            if (epoch > 0) Instant.ofEpochSecond(epoch) else null
        } catch (e: StripeException) {
            log.warn("Could not retrieve subscription {} for period end: {}", subscriptionId, e.message)
            null
        }
    }

    private fun updateSubscriptionDiscount(user: User, apply: Boolean) {
        val subId = user.stripeSubscriptionId
        if (props.apiKey.isBlank() || subId.isNullOrBlank()) return
        if (apply && props.creatorCouponId.isBlank()) return
        val params = if (apply) {
            SubscriptionUpdateParams.builder()
                .addDiscount(
                    SubscriptionUpdateParams.Discount.builder().setCoupon(props.creatorCouponId).build()
                )
                .build()
        } else {
            // An empty discounts list clears any existing discount.
            SubscriptionUpdateParams.builder().setDiscounts(EmptyParam.EMPTY).build()
        }
        try {
            StripeClient(props.apiKey).subscriptions().update(subId, params)
            log.info("{} creator discount for user={} sub={}", if (apply) "Applied" else "Removed", user.id, subId)
        } catch (e: StripeException) {
            log.warn(
                "Failed to {} creator discount for user={} sub={}: {}",
                if (apply) "apply" else "remove", user.id, subId, e.message
            )
        }
    }
}
