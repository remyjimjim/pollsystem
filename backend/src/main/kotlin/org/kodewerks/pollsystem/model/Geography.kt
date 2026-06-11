package org.kodewerks.pollsystem.model

import jakarta.persistence.*

@Entity
@Table(name = "states")
data class State(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, length = 2)
    val initial: String
)

@Entity
@Table(name = "counties")
data class County(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id", nullable = false)
    val state: State,

    @Column(nullable = false)
    val name: String
)

@Entity
@Table(name = "county_zips")
data class CountyZips(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "county_id", nullable = false)
    val county: County,

    @Column(nullable = false, length = 5)
    val zipcode: String
)
