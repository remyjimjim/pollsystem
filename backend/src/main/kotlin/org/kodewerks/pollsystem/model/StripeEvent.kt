package org.kodewerks.pollsystem.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "stripe_events")
data class StripeEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "stripe_event_id", nullable = false, unique = true, length = 64)
    val stripeEventId: String,

    @Column(name = "event_type", nullable = false, length = 64)
    val eventType: String,

    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant = Instant.now()
)
