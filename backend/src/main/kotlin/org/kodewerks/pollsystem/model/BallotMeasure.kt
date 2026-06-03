package com.pollsystem.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "ballot_measures")
data class BallotMeasure(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    val creator: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_type_id", nullable = false)
    val pollType: PollType,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val summary: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    val election: Election,

    @Column(name = "effective_date", nullable = false)
    val effectiveDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "poll_status")
    val status: PollStatus = PollStatus.DRAFT,

    @Column(name = "close_date")
    val closeDate: Instant? = null,

    @Column(name = "date_created", nullable = false)
    val dateCreated: Instant = Instant.now(),

    @Column(name = "last_updated", nullable = false)
    val lastUpdated: Instant = Instant.now()
)

@Entity
@Table(
    name = "ballot_responses",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "measure_id"])]
)
data class BallotResponse(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measure_id", nullable = false)
    val measure: BallotMeasure,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    val response: Boolean,

    @Column(columnDefinition = "TEXT")
    val comment: String? = null,

    @Column(name = "date_submitted", nullable = false)
    val dateSubmitted: Instant = Instant.now(),

    @Column(name = "last_modified")
    val lastModified: Instant? = null
)
