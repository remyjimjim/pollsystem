package org.kodewerks.pollsystem.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Singleton row (id is always 1, guarded by a CHECK constraint) holding
 * global platform flags. See V21.
 */
@Entity
@Table(name = "app_flags")
data class AppFlag(
    @Id
    val id: Int = 1,

    @Column(name = "polls_disabled", nullable = false)
    val pollsDisabled: Boolean = false,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),

    @Column(name = "updated_by")
    val updatedBy: Long? = null
)
