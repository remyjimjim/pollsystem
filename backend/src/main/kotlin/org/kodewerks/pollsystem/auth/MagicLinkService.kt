package org.kodewerks.pollsystem.auth

import org.kodewerks.pollsystem.model.MagicLinkToken
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.MagicLinkTokenRepository
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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

/** A sign-in email's link token and its 6-digit code, both raw (only hashes are stored). */
data class IssuedSignIn(val token: String, val code: String)

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

    fun issueToken(user: User): IssuedSignIn {
        val raw = randomTokenHex()
        val code = "%06d".format(random.nextInt(1_000_000))
        val tokenHash = sha256(raw)
        tokens.save(
            MagicLinkToken(
                tokenHash = tokenHash,
                userId = user.id,
                expiresAt = Instant.now().plus(Duration.ofMinutes(props.ttlMinutes)),
                codeHash = sha256("$code:$tokenHash")
            )
        )
        return IssuedSignIn(raw, code)
    }

    /**
     * Sign in with the 6-digit code from a sign-in email instead of its link
     * (decided 2026-10-27): for phones, where the email app opens links in its
     * own browser. Matches any live link of [userId]'s; using it uses up that
     * link. A wrong code counts against every live link of theirs, and after
     * [MAX_CODE_ATTEMPTS] a link's code stops working (its link still works).
     */
    @Transactional
    fun redeemCode(userId: Long, code: String): Boolean {
        val digits = code.filter(Char::isDigit)
        val now = Instant.now()
        val live = tokens.findByUserId(userId).filter {
            it.usedAt == null && it.expiresAt.isAfter(now) && it.codeHash != null && it.codeAttempts < MAX_CODE_ATTEMPTS
        }
        if (live.isEmpty()) return false
        val match = if (digits.length == 6) live.firstOrNull {
            MessageDigest.isEqual(sha256("$digits:${it.tokenHash}").toByteArray(), it.codeHash!!.toByteArray())
        } else null
        if (match == null) {
            live.forEach { tokens.save(it.copy(codeAttempts = it.codeAttempts + 1)) }
            return false
        }
        tokens.save(match.copy(usedAt = now))
        return true
    }

    companion object { const val MAX_CODE_ATTEMPTS = 5 }

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
