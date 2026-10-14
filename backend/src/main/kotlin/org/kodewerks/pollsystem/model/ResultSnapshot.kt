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

/** A poll's public results as of its last step of k respondents (V30, see ResultSnapshotService). */
@Entity
@Table(name = "result_snapshots")
data class ResultSnapshot(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "poll_type", nullable = false, length = 20)
    val pollType: PollKind,

    @Column(name = "poll_id", nullable = false)
    val pollId: Long,

    @Column(nullable = false)
    val respondents: Int,

    @Column(name = "taken_at", nullable = false)
    val takenAt: Instant = Instant.now()
)

/** One frozen answer in a [ResultSnapshot]. */
@Entity
@Table(name = "result_snapshot_answers")
data class ResultSnapshotAnswer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "snapshot_id", nullable = false)
    val snapshotId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(length = 5)
    val zipcode: String?,

    /** The question, candidate, or the measure itself. */
    @Column(name = "item_id", nullable = false)
    val itemId: Long,

    /** The answer text, or "true"/"false". */
    @Column(nullable = false)
    val response: String
)
