package com.pollsystem.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "admin_requests")
data class AdminRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: RequestStatus = RequestStatus.PENDING,

    @Column(nullable = false, columnDefinition = "TEXT")
    val reason: String,

    @Column(name = "submitted_at", nullable = false)
    val submittedAt: Instant = Instant.now(),

    @Column(name = "processed_at")
    val processedAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_id")
    val processedBy: User? = null
)
