package org.kodewerks.pollsystem.model

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

    // Grant granularity. ZIP (default) fills state+county+zipcode; COUNTY fills
    // state+county; STATE fills state; NATIONAL leaves all three null.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "scope_level", nullable = false, columnDefinition = "scope_level")
    val scopeLevel: ScopeLevel = ScopeLevel.ZIP,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_type_id")
    val pollType: PollType? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id")
    val state: State? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "county_id")
    val county: County? = null,

    @Column(length = 5)
    val zipcode: String? = null,

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
