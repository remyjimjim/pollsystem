package org.kodewerks.pollsystem.payment

import org.kodewerks.pollsystem.auth.MagicLinkEmailer
import org.kodewerks.pollsystem.auth.MagicLinkProperties
import org.kodewerks.pollsystem.auth.MagicLinkService
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * Offline stand-in for Stripe, for local dev without any Stripe keys or webhooks
 * (docker-compose sets `app.payments.provider=mock`). It simulates the whole
 * subscription round-trip locally: "checkout" immediately provisions/renews a
 * paid account and — for a new registrant — emails the magic link (which lands
 * in Mailpit at http://localhost:8025), then returns the same `?checkout=success`
 * redirect the real flow uses. So register → "Continue to payment" → check
 * Mailpit → sign in, all works with no network.
 *
 * `@Primary` so it wins over [org.kodewerks.pollsystem.stripe.StripePaymentProvider]
 * (which stays registered — the Stripe webhook injects it directly) whenever the
 * property is set. It is registered ONLY when `app.payments.provider=mock`, so
 * staging/prod (which never set it) are unaffected.
 */
@Service
@Primary
@ConditionalOnProperty(name = ["app.payments.provider"], havingValue = "mock")
class MockPaymentProvider(
    private val users: UserRepository,
    private val magicLinks: MagicLinkService,
    private val emailer: MagicLinkEmailer,
    private val magicLink: MagicLinkProperties,
) : PaymentProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    private val baseUrl: String get() = magicLink.baseUrl.trimEnd('/')
    private val period = Duration.ofDays(30)

    init {
        log.warn("MockPaymentProvider active — payments are SIMULATED locally (no Stripe). Local dev only.")
    }

    /** Simulate a completed guest subscription checkout: provision a paid, complete account + magic link. */
    override fun createGuestCheckoutSession(email: String, phone: String, zipcode: String): String {
        val normalized = email.lowercase()
        // AuthController already validated that email/phone are free and the
        // zipcode is real before calling this, so we can just create the account.
        val user = users.save(
            User(
                email = normalized,
                phone = phone,
                zipcode = zipcode,
                access = AccessLevel.USER,
                isEnabled = true,
                stripeCustomerId = "cus_mock_${System.nanoTime()}",
                stripeSubscriptionId = "sub_mock_${System.nanoTime()}",
                paidUntil = Instant.now().plus(period),
            )
        )
        emailer.send(user, magicLinks.issueToken(user))
        log.info("Mock checkout: provisioned paid user {} and emailed a magic link (see Mailpit)", normalized)
        return "$baseUrl/?checkout=success&mock=1"
    }

    /** Simulate a re-subscribe / renewal for an existing signed-in user. */
    override fun createCheckoutSession(user: User): String {
        val access = if (user.access == AccessLevel.VIEWER) AccessLevel.USER else user.access
        users.save(
            user.copy(
                paidUntil = Instant.now().plus(period),
                access = access,
                stripeSubscriptionId = user.stripeSubscriptionId ?: "sub_mock_${System.nanoTime()}",
            )
        )
        log.info("Mock checkout: renewed membership for user {}", user.id)
        return "$baseUrl/?checkout=success&mock=1"
    }

    /** No real portal offline — just send them back to the home page. */
    override fun createPortalSession(user: User): String {
        log.info("Mock portal for user {} (no-op)", user.id)
        return "$baseUrl/?portal=mock"
    }

    override fun applyCreatorDiscount(user: User) {
        log.info("Mock: applyCreatorDiscount (no-op) for user {}", user.id)
    }

    override fun removeCreatorDiscount(user: User) {
        log.info("Mock: removeCreatorDiscount (no-op) for user {}", user.id)
    }
}
