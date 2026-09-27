package org.kodewerks.pollsystem.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * One row of a poll's purview (territorial scope). Polymorphic over the three
 * poll kinds via (pollType, pollId). A poll may have many rows, all at the same
 * scope level. Ballot measures store no rows — they inherit their election's.
 *
 * Geo columns follow the [ScopeLevel] convention: ZIP fills [zipcode]; COUNTY
 * fills [countyId]; STATE fills [stateId]; NATIONAL leaves all null. See V22.
 */
@Entity
@Table(name = "poll_purviews")
data class PollPurview(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "poll_type", nullable = false, length = 32)
    val pollType: PollKind,

    @Column(name = "poll_id", nullable = false)
    val pollId: Long,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "scope_level", nullable = false, columnDefinition = "scope_level")
    val scopeLevel: ScopeLevel,

    @Column(name = "state_id")
    val stateId: Long? = null,

    @Column(name = "county_id")
    val countyId: Long? = null,

    @Column(length = 5)
    val zipcode: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
