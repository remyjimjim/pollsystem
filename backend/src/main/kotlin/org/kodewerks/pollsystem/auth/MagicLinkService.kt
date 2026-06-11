package org.kodewerks.pollsystem.auth

import org.kodewerks.pollsystem.model.MagicLinkToken
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.MagicLinkTokenRepository
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.HexFormat

@ConfigurationProperties(prefix = "app.magic-link")
data class MagicLinkProperties(
    val ttlMinutes: Long = 15,
    val baseUrl: String = "http://localhost:3000"
)

/**
 * Issues and redeems one-shot magic-link tokens. The raw token is returned
 * once at issue time (for the caller to deliver via email); only its SHA-256
 * hash is persisted, so a DB leak does not expose live tokens.
 */
@Service
class MagicLinkService(
    private val tokens: MagicLinkTokenRepository,
    private val props: MagicLinkProperties
) {
    private val random = SecureRandom()

    fun issueToken(user: User): String {
        val raw = randomTokenHex()
        tokens.save(
            MagicLinkToken(
                tokenHash = sha256(raw),
                userId = user.id,
                expiresAt = Instant.now().plus(Duration.ofMinutes(props.ttlMinutes))
            )
        )
        return raw
    }

    /**
     * Looks up the raw token, validates it, and marks it consumed. Returns the
     * userId on success, null otherwise. Single-use: a successful redeem
     * cannot be repeated.
     */
    fun redeem(rawToken: String): Long? {
        val record = tokens.findByTokenHash(sha256(rawToken)) ?: return null
        val now = Instant.now()
        if (record.usedAt != null) return null
        if (record.expiresAt.isBefore(now)) return null
        tokens.save(record.copy(usedAt = now))
        return record.userId
    }

    fun buildRedeemUrl(rawToken: String): String =
        "${props.baseUrl.trimEnd('/')}/auth/magic-link?token=$rawToken"

    private fun randomTokenHex(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return HexFormat.of().formatHex(bytes)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
