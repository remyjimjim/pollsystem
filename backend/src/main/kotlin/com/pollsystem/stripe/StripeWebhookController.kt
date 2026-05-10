package com.pollsystem.stripe

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/webhooks")
class StripeWebhookController(
    private val verifier: StripeSignatureVerifier,
    private val service: StripeWebhookService,
    private val json: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/stripe")
    fun handle(
        @RequestBody rawBody: String,
        @RequestHeader(name = "Stripe-Signature", required = false) signature: String?
    ): ResponseEntity<String> {
        if (!verifier.verify(rawBody, signature)) {
            log.warn("Rejecting Stripe webhook with bad signature")
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signature")
        }
        val event = try {
            json.readTree(rawBody)
        } catch (e: Exception) {
            log.warn("Stripe webhook body is not valid JSON: {}", e.message)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid json")
        }
        service.process(event)
        return ResponseEntity.ok("ok")
    }
}
