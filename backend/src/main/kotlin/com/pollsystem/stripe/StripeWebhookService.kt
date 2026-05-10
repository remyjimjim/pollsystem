package com.pollsystem.stripe

import com.fasterxml.jackson.databind.JsonNode
import com.pollsystem.model.StripeEvent
import com.pollsystem.repository.StripeEventRepository
import com.pollsystem.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Applies Stripe webhook events to user state. Idempotent on `event.id`:
 * a duplicate delivery becomes a no-op.
 *
 * Supported events:
 *  - checkout.session.completed       — link Stripe customer + subscription to existing user
 *  - customer.subscription.updated    — refresh paid_until from current_period_end
 *  - customer.subscription.deleted    — clear paid_until (subscriber lost access)
 *  - invoice.paid                     — refresh paid_until on renewal
 *  - invoice.payment_failed           — log only; access is dropped on subscription.deleted
 */
@Service
class StripeWebhookService(
    private val users: UserRepository,
    private val events: StripeEventRepository
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
        val user = users.findByEmail(email)
        if (user == null) {
            log.warn("checkout.session.completed for {} matches no existing user; ignoring (user must sign in via magic-link first)", email)
            return
        }
        users.save(user.copy(
            stripeCustomerId = customerId ?: user.stripeCustomerId,
            stripeSubscriptionId = subscriptionId ?: user.stripeSubscriptionId
        ))
    }

    private fun handleSubscriptionRefresh(data: JsonNode, type: String) {
        val (subscriptionId, periodEndEpoch) = when (type) {
            "customer.subscription.updated" ->
                data.path("id").asText() to data.path("current_period_end").asLong(0)
            "invoice.paid" ->
                data.path("subscription").asText() to data.path("lines").path("data").firstOrNull()
                    ?.path("period")?.path("end")?.asLong(0).let { it ?: 0L }
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
        users.save(user.copy(paidUntil = Instant.ofEpochSecond(periodEndEpoch)))
    }

    private fun handleSubscriptionDeleted(data: JsonNode) {
        val subscriptionId = data.path("id").asText().takeIf { it.isNotBlank() } ?: return
        val user = users.findByStripeSubscriptionId(subscriptionId) ?: return
        users.save(user.copy(paidUntil = null, stripeSubscriptionId = null))
    }
}
