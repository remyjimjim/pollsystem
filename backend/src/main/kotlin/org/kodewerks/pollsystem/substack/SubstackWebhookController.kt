package org.kodewerks.pollsystem.substack

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest

/**
 * Inbound Substack membership webhook (generic + secret-gated). Substack's exact
 * payload/auth contract is TBD — it may arrive via a relay (Zapier, its API) —
 * so this deliberately accepts a minimal `{email, event}` body and a shared
 * secret header, and maps a permissive set of event names onto activate /
 * deactivate. Tighten the mapping once the real Substack contract is wired up.
 */
@RestController
@RequestMapping("/webhooks")
class SubstackWebhookController(
    private val service: SubstackWebhookService,
    private val props: SubstackProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/substack")
    fun handle(
        @RequestHeader(name = "X-Webhook-Secret", required = false) secret: String?,
        @Valid @RequestBody req: SubstackWebhookRequest
    ): ResponseEntity<String> {
        if (props.webhookSecret.isBlank()) {
            log.warn("Substack webhook hit but no secret is configured; rejecting")
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("not configured")
        }
        if (!constantTimeEquals(secret, props.webhookSecret)) {
            log.warn("Rejecting Substack webhook with bad or missing secret")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid secret")
        }
        when (req.event.lowercase()) {
            "subscribed", "subscription.created", "renewed", "renewal", "invoice.paid", "active" ->
                service.activate(req.email)
            "unsubscribed", "subscription.deleted", "canceled", "cancelled", "inactive", "expired" ->
                service.deactivate(req.email)
            else -> {
                log.info("Ignoring unhandled Substack event: {}", req.event)
                return ResponseEntity.ok("ignored")
            }
        }
        return ResponseEntity.ok("ok")
    }

    // Length-independent equality to avoid leaking the secret via timing.
    private fun constantTimeEquals(provided: String?, expected: String): Boolean {
        if (provided == null) return false
        return MessageDigest.isEqual(
            provided.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
    }
}

/** Minimal Substack event payload: who, and what happened. */
data class SubstackWebhookRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val event: String
)
