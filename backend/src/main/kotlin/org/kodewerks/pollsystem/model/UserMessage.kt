package org.kodewerks.pollsystem.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "user_messages")
data class UserMessage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "author_id", nullable = false)
    val authorId: Long,

    @Column(nullable = false, length = 2000)
    val body: String,

    @Column(nullable = false)
    val emailed: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
