package org.kodewerks.pollsystem.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "poll_types")
data class PollType(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "poll_type", nullable = false)
    val pollType: Int,

    @Column(nullable = false)
    val name: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_json", nullable = false, columnDefinition = "jsonb")
    val templateJson: String = "{}"
)
