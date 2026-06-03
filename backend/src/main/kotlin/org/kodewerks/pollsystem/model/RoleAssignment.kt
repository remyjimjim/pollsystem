package com.pollsystem.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "role_assignments")
data class RoleAssignment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "access_level")
    val role: AccessLevel,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_type_id")
    val pollType: PollType? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id", nullable = false)
    val state: State,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "county_id", nullable = false)
    val county: County,

    @Column(nullable = false, length = 5)
    val zipcode: String,

    @Column(nullable = false)
    val enabled: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_request_id")
    val creatorRequest: CreatorRequest? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_request_id")
    val adminRequest: AdminRequest? = null,

    @Column(name = "assigned_at", nullable = false)
    val assignedAt: Instant = Instant.now()
)
