package com.pollsystem.stripe

import org.springframework.stereotype.Component
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Verifies the `Stripe-Signature` header per
 * https://stripe.com/docs/webhooks/signatures using HMAC-SHA256 only — no
 * Stripe SDK dependency. Rejects timestamps older than the configured
 * tolerance to prevent replay.
 */
@Component
class StripeSignatureVerifier(private val props: StripeProperties) {

    fun verify(rawPayload: String, signatureHeader: String?): Boolean {
        if (props.webhookSecret.isBlank() || signatureHeader.isNullOrBlank()) return false

        val parts = signatureHeader.split(',')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.substring(0, idx).trim() to part.substring(idx + 1).trim()
            }

        val timestamp = parts.firstOrNull { it.first == "t" }?.second?.toLongOrNull() ?: return false
        val v1Sigs = parts.filter { it.first == "v1" }.map { it.second }
        if (v1Sigs.isEmpty()) return false

        val now = Instant.now().epochSecond
        if (now - timestamp > props.toleranceSeconds) return false

        val expected = hmacSha256Hex(props.webhookSecret, "$timestamp.$rawPayload")
        return v1Sigs.any { constantTimeEquals(it, expected) }
    }

    private fun hmacSha256Hex(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(raw.size * 2)
        for (b in raw) sb.append("%02x".format(b))
        return sb.toString()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
