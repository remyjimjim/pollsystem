package com.pollsystem.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false, unique = true)
    val phone: String,

    @Column(nullable = false, length = 5)
    val zipcode: String,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "access_level")
    val access: AccessLevel = AccessLevel.VIEWER,

    @Column(name = "is_enabled", nullable = false)
    val isEnabled: Boolean = true,

    @Column(name = "stripe_customer_id", length = 64)
    val stripeCustomerId: String? = null,

    @Column(name = "stripe_subscription_id", length = 64)
    val stripeSubscriptionId: String? = null,

    @Column(name = "paid_until")
    val paidUntil: Instant? = null
)
