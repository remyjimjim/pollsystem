package org.kodewerks.pollsystem.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

enum class PollEventType {
    PUBLISHED, REOPENED, CLOSED, ARCHIVED, RESTORED,
    SWITCHED_OFF, SWITCHED_ON, EDITED, PUBLISHED_BEFORE_HISTORY
}

/** One entry in a poll's history (V34). [actor] null = automatic (e.g. closing). */
@Entity
@Table(name = "poll_events")
data class PollEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "poll_type", nullable = false, length = 20)
    val pollType: PollKind,

    @Column(name = "poll_id", nullable = false)
    val pollId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val event: PollEventType,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    val actor: User? = null,

    @Column(nullable = false)
    val at: Instant = Instant.now(),

    @Column(columnDefinition = "TEXT")
    val details: String? = null,

    @Column(columnDefinition = "TEXT")
    val snapshot: String? = null
)
