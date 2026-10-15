package org.kodewerks.pollsystem.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class PollNotificationKind { RESULTS_VISIBLE, CLOSED }

/** A milestone email sent to a poll's creator (V31); at most one per poll and kind. */
@Entity
@Table(name = "poll_notifications")
data class PollNotification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "poll_type", nullable = false, length = 20)
    val pollType: PollKind,

    @Column(name = "poll_id", nullable = false)
    val pollId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val kind: PollNotificationKind,

    @Column(name = "sent_at", nullable = false)
    val sentAt: Instant = Instant.now()
)

/** Milestone emails paused for a poll (V32); no row = emails on. */
@Entity
@Table(name = "poll_email_mutes")
data class PollEmailMute(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "poll_type", nullable = false, length = 20)
    val pollType: PollKind,

    @Column(name = "poll_id", nullable = false)
    val pollId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
