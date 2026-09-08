package org.kodewerks.pollsystem.stripe

import com.stripe.StripeClient
import com.stripe.exception.StripeException
import com.stripe.param.billingportal.SessionCreateParams as PortalSessionCreateParams
import com.stripe.param.checkout.SessionCreateParams
import org.kodewerks.pollsystem.auth.MagicLinkProperties
import org.kodewerks.pollsystem.model.User
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * Creates Stripe-hosted Checkout and Customer Portal sessions for the Creator
 * subscription. This is the ONE place the app calls Stripe's API — everything
 * else (the webhook) is keyless — so it's gated behind `app.stripe.api-key`
 * and returns 503 until that (and the price id) are configured.
 *
 * Checkout uses `client_reference_id = user.id` so the webhook can link the
 * resulting subscription to this exact user rather than matching by email.
 */
@Service
class BillingService(
    private val props: StripeProperties,
    private val magicLink: MagicLinkProperties
) {
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
    fun createCheckoutSession(user: User): String {
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

    /** Create a Customer Portal session for [user]; returns the redirect URL. */
    fun createPortalSession(user: User): String {
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
}
