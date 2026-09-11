package org.kodewerks.pollsystem.stripe

import com.fasterxml.jackson.databind.JsonNode
import org.kodewerks.pollsystem.auth.MagicLinkEmailer
import org.kodewerks.pollsystem.auth.MagicLinkService
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.StripeEvent
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.StripeEventRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Applies Stripe webhook events to user state. Idempotent on `event.id`:
 * a duplicate delivery becomes a no-op.
 *
 * Supported events:
 *  - checkout.session.completed       — provision a new paid user (email only) and
 *                                       email a magic link, or link Stripe ids to an
 *                                       existing user
 *  - customer.subscription.updated    — refresh paid_until from current_period_end
 *  - customer.subscription.deleted    — clear paid_until (subscriber lost access)
 *  - invoice.paid                     — refresh paid_until on renewal
 *  - invoice.payment_failed           — log only; access is dropped on subscription.deleted
 */
@Service
class StripeWebhookService(
    private val users: UserRepository,
    private val events: StripeEventRepository,
    private val magicLinks: MagicLinkService,
    private val emailer: MagicLinkEmailer
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun process(event: JsonNode) {
        val id = event.path("id").asText()
        val type = event.path("type").asText()
        if (id.isBlank() || type.isBlank()) {
            log.warn("Stripe event missing id or type; ignoring")
            return
        }
        if (events.existsByStripeEventId(id)) {
            log.debug("Stripe event {} already processed; skipping", id)
            return
        }
        events.save(StripeEvent(stripeEventId = id, eventType = type))

        val data = event.path("data").path("object")
        when (type) {
            "checkout.session.completed" -> handleCheckoutCompleted(data)
            "customer.subscription.updated", "invoice.paid" -> handleSubscriptionRefresh(data, type)
            "customer.subscription.deleted" -> handleSubscriptionDeleted(data)
            "invoice.payment_failed" -> log.info("Stripe payment failed for customer={}", data.path("customer").asText())
            else -> log.debug("Unhandled Stripe event type: {}", type)
        }
    }

    private fun handleCheckoutCompleted(data: JsonNode) {
        val email = data.path("customer_details").path("email").asText().lowercase()
        val customerId = data.path("customer").asText().takeIf { it.isNotBlank() }
        val subscriptionId = data.path("subscription").asText().takeIf { it.isNotBlank() }
        if (email.isBlank()) {
            log.warn("checkout.session.completed missing customer_details.email")
            return
        }
        val existing = users.findByEmail(email)
        if (existing != null) {
            // A re-subscribing lapsed VIEWER comes back as USER (they re-apply
            // for any former creator/admin role); an active USER+ keeps its level.
            val access = if (existing.access == AccessLevel.VIEWER) AccessLevel.USER else existing.access
            users.save(existing.copy(
                stripeCustomerId = customerId ?: existing.stripeCustomerId,
                stripeSubscriptionId = subscriptionId ?: existing.stripeSubscriptionId,
                access = access
            ))
            return
        }

        // Payment-first onboarding: no account yet, so provision a minimal paid
        // user from the checkout email alone (phone + zipcode are collected when
        // they complete their profile at first sign-in) and email a magic link so
        // they can get in. paid_until is set by the subsequent subscription/invoice
        // event, which finds this user by stripe_subscription_id.
        val provisioned = users.save(
            User(
                email = email,
                access = AccessLevel.USER,
                isEnabled = true,
                stripeCustomerId = customerId,
                stripeSubscriptionId = subscriptionId
            )
        )
        val rawToken = magicLinks.issueToken(provisioned)
        emailer.send(provisioned, rawToken)
        log.info("Provisioned paid user {} from checkout and emailed a magic link", email)
    }

    private fun handleSubscriptionRefresh(data: JsonNode, type: String) {
        val (subscriptionId, periodEndEpoch) = when (type) {
            "customer.subscription.updated" ->
                data.path("id").asText() to subscriptionPeriodEnd(data)
            "invoice.paid" ->
                invoiceSubscriptionId(data) to invoicePeriodEnd(data)
            else -> return
        }
        if (subscriptionId.isBlank() || periodEndEpoch <= 0) {
            log.warn("Stripe {} missing subscription id or period end", type)
            return
        }
        val user = users.findByStripeSubscriptionId(subscriptionId)
        if (user == null) {
            log.warn("Stripe {} for subscription={} matches no user", type, subscriptionId)
            return
        }
        // Reactivating a lapsed VIEWER via a paid invoice/renewal restores USER.
        val access = if (user.access == AccessLevel.VIEWER) AccessLevel.USER else user.access
        users.save(user.copy(paidUntil = Instant.ofEpochSecond(periodEndEpoch), access = access))
    }

    /**
     * Current period end for a subscription object. Stripe's 2025 API versions
     * moved `current_period_end` off the Subscription and onto each subscription
     * item, so read the top-level field (older versions) and fall back to the
     * first item (newer versions). Without this, a newer API version reports 0
     * and paid_until silently stops refreshing at renewal.
     */
    private fun subscriptionPeriodEnd(subscription: JsonNode): Long {
        val topLevel = subscription.path("current_period_end").asLong(0)
        if (topLevel > 0) return topLevel
        return subscription.path("items").path("data").firstOrNull()
            ?.path("current_period_end")?.asLong(0) ?: 0L
    }

    /**
     * Subscription id on an invoice: top-level `subscription` for older API
     * versions, or `parent.subscription_details.subscription` for
     * 2025-03-31.basil and later.
     */
    private fun invoiceSubscriptionId(invoice: JsonNode): String {
        val topLevel = invoice.path("subscription").asText("")
        if (topLevel.isNotBlank()) return topLevel
        return invoice.path("parent").path("subscription_details").path("subscription").asText("")
    }

    /**
     * New period end from an invoice: the latest `period.end` across all line
     * items. An invoice can carry multiple lines (e.g. a proration credit whose
     * period is in the past plus the subscription line for the new period); the
     * max is the renewal we want, so this is safer than taking the first line.
     */
    private fun invoicePeriodEnd(invoice: JsonNode): Long =
        invoice.path("lines").path("data")
            .maxOfOrNull { it.path("period").path("end").asLong(0) } ?: 0L

    private fun handleSubscriptionDeleted(data: JsonNode) {
        val subscriptionId = data.path("id").asText().takeIf { it.isNotBlank() } ?: return
        val user = users.findByStripeSubscriptionId(subscriptionId) ?: return
        // A lapsed subscription demotes the account to VIEWER (except SUPER): it
        // loses participation and any elevated role, and must re-apply for
        // creator/admin after re-subscribing. VIEWER thus legitimately appears
        // in the DB as this dormant state, distinct from anonymous viewers.
        val demoted = if (user.access == AccessLevel.SUPER) user.access else AccessLevel.VIEWER
        users.save(user.copy(paidUntil = null, stripeSubscriptionId = null, access = demoted))
    }
}
