package com.pollsystem.model

import jakarta.persistence.*

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

    @Column(nullable = false)
    val passcode: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val access: AccessLevel = AccessLevel.VIEWER,

    @Column(name = "is_enabled", nullable = false)
    val isEnabled: Boolean = true
)
