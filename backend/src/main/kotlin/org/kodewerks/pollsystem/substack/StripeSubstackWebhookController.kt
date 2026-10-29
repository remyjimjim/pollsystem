package org.kodewerks.pollsystem.substack

import com.fasterxml.jackson.databind.ObjectMapper
import org.kodewerks.pollsystem.stripe.StripeSignatureVerifier
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/**
 * Webhook from the Substack Stripe account (norfstar.substack), never the
 * BallotBox one: invoice.paid makes or renews a member through the paid period
 * (plus a day, so a renewal arriving a little late doesn't lapse them);
 * customer.subscription.deleted, or an update to canceled / unpaid /
 * incomplete_expired, ends it. Anything else is acknowledged and ignored.
 * Blank signing secret: 503 (off). A failure answers 500 so Stripe retries.
 */
@RestController
@RequestMapping("/webhooks")
class StripeSubstackWebhookController(
    private val props: StripeSubstackProperties,
    private val verifier: StripeSignatureVerifier,
    private val membership: SubstackMembership,
    private val syncLog: SubstackSyncLog,
    private val json: ObjectMapper
) {
    @PostMapping("/stripe-substack")
    fun handle(
        @RequestBody raw: String,
        @RequestHeader(name = "Stripe-Signature", required = false) signature: String?
    ): ResponseEntity<String> {
        if (props.webhookSecret.isBlank()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("not configured")
        if (!verifier.verify(raw, signature, props.webhookSecret)) {
            syncLog.error(SOURCE, "Rejected a webhook with a bad signature (check STRIPE_SUBSTACK_WEBHOOK_SECRET)")
            return ResponseEntity.badRequest().body("invalid signature")
        }
        return try {
            val event = json.readTree(raw)
            val type = event.path("type").asText()
            val obj = event.path("data").path("object")
            when (type) {
                "invoice.paid", "invoice.payment_succeeded" -> {
                    val email = obj.path("customer_email").asText(null)
                    val customer = StripeSubstackJson.customerId(obj)
                    val until = StripeSubstackJson.invoicePaidThrough(obj)
                    if (email.isNullOrBlank() || customer == null || until == null) {
                        syncLog.error(SOURCE, "$type ${obj.path("id").asText()} has no customer email, customer or period end")
                        return ResponseEntity.ok("ignored")
                    }
                    membership.paidThrough(email, customer, until.plus(Duration.ofDays(1)), SOURCE)
                }
                "customer.subscription.deleted" -> StripeSubstackJson.customerId(obj)?.let { membership.ended(it, SOURCE) }
                "customer.subscription.updated" -> {
                    val status = obj.path("status").asText()
                    if (status in setOf("canceled", "unpaid", "incomplete_expired")) StripeSubstackJson.customerId(obj)?.let { membership.ended(it, SOURCE) }
                }
                else -> return ResponseEntity.ok("ignored")
            }
            ResponseEntity.ok("ok")
        } catch (e: Exception) {
            syncLog.error(SOURCE, "Webhook failed: ${e.message}")
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error")
        }
    }

    companion object { const val SOURCE = "webhook" }
}
