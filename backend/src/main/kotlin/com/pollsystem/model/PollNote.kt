package com.pollsystem.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "poll_notes")
data class PollNote(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "poll_type", nullable = false, length = 32)
    val pollType: PollKind,

    @Column(name = "poll_id", nullable = false)
    val pollId: Long,

    @Column(nullable = false, length = 2000)
    val body: String,

    @Column(name = "author_id", nullable = false)
    val authorId: Long,

    @Column(nullable = false)
    val emailed: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
