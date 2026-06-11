package org.kodewerks.pollsystem.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "magic_link_tokens")
data class MagicLinkToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "issued_at", nullable = false)
    val issuedAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "used_at")
    val usedAt: Instant? = null
)
