package com.pollsystem.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "offices")
data class Office(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val desc: String,

    @Column(name = "date_created", nullable = false)
    val dateCreated: Instant = Instant.now(),

    @Column(name = "last_updated", nullable = false)
    val lastUpdated: Instant = Instant.now()
)

@Entity
@Table(name = "elections")
data class Election(
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

    @Column(nullable = false)
    val date: LocalDate,

    @Column(nullable = false, length = 5)
    val zipcode: String,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "poll_status")
    val status: PollStatus = PollStatus.DRAFT,

    @Column(name = "close_date")
    val closeDate: Instant? = null,

    @Column(name = "date_submitted", nullable = false)
    val dateSubmitted: Instant = Instant.now()
)

@Entity
@Table(name = "candidates")
data class Candidate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val affiliation: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id", nullable = false)
    val office: Office,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    val election: Election,

    @Column(name = "create_date", nullable = false)
    val createDate: LocalDate = LocalDate.now()
)

@Entity
@Table(
    name = "candidate_responses",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "candidate_id"])]
)
data class CandidateResponse(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    val candidate: Candidate,

    @Column(nullable = false)
    val response: Boolean,

    @Column(columnDefinition = "TEXT")
    val comment: String? = null,

    @Column(name = "date_submitted", nullable = false)
    val dateSubmitted: Instant = Instant.now(),

    @Column(name = "last_modified")
    val lastModified: Instant? = null
)
