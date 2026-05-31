package com.pollsystem.model

import jakarta.persistence.*
import java.time.Instant

enum class PollKind { ELECTION, QUESTIONNAIRE, BALLOT_MEASURE }

enum class BlockScope { ZIPCODE, COUNTY, STATE }

@Entity
@Table(name = "poll_type_blocks")
data class PollTypeBlock(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "poll_type", nullable = false, length = 32)
    val pollType: PollKind,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val scope: BlockScope,

    @Column(length = 5)
    val zipcode: String? = null,

    @Column(name = "county_id")
    val countyId: Long? = null,

    @Column(name = "state_id")
    val stateId: Long? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "created_by", nullable = false)
    val createdBy: Long
)
