package org.kodewerks.pollsystem.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "questionnaires")
data class Questionnaire(
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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "poll_status")
    val status: PollStatus = PollStatus.DRAFT,

    @Column(name = "close_date")
    val closeDate: Instant? = null,

    @Column(name = "create_date", nullable = false)
    val createDate: LocalDate = LocalDate.now(),

    @Column(name = "submit_date")
    val submitDate: Instant? = null
)

// questionnaire_domains was retired in V24: a questionnaire's geo now lives in
// poll_purviews (see [[project_poll_purview]]), unified with the other poll types.

@Entity
@Table(name = "questions")
data class Question(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questionnaire_id", nullable = false)
    val questionnaire: Questionnaire,

    @Column(nullable = false, columnDefinition = "TEXT")
    val question: String
)

@Entity
@Table(
    name = "question_responses",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "question_id"])]
)
data class QuestionResponse(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    val question: Question,

    @Column(nullable = false, columnDefinition = "TEXT")
    val response: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(columnDefinition = "TEXT")
    val comment: String? = null,

    @Column(name = "date_submitted", nullable = false)
    val dateSubmitted: Instant = Instant.now(),

    @Column(name = "last_modified")
    val lastModified: Instant? = null
)
