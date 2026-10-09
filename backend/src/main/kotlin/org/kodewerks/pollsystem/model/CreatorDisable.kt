package org.kodewerks.pollsystem.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Admin [adminId] has disabled creator [creatorId] within the admin's purview
 * (the Enabled checkbox on /admin/manage-creators). See V26.
 */
@Entity
@Table(name = "creator_disables")
data class CreatorDisable(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "creator_id", nullable = false)
    val creatorId: Long,

    @Column(name = "admin_id", nullable = false)
    val adminId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    /** Why the admin disabled this creator (optional, V29). */
    @Column(length = 500)
    val reason: String? = null
)
