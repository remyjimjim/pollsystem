package com.pollsystem

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer

// Singleton-container pattern (Spring Boot's recommended Testcontainers setup):
// the container is started once per JVM in a static initialiser and never
// stopped — JUnit's @Testcontainers lifecycle would call stop()/start() between
// classes, which races with Spring's cached test contexts and surfaces as
// "Connection refused" on a long-since-changed host port. Letting the JVM
// shutdown hook handle cleanup gives every test class the same container with
// a stable host port. Flyway migrations V1..V7 run on first context boot.
//
// Reuse is intentionally NOT enabled: cross-run pollution surfaces as duplicate-
// key errors on hard-coded test emails and assertion drift on auto-incrementing
// ids. Each `gradle test` pays one ~5–10s container start; in exchange every
// run starts with a clean database.
//
// @Transactional rolls each test method back at end, so within a run no test
// class can leak data into another. If a specific test needs committed data
// (rare — typically only relevant for testing scheduled or async behaviour),
// override with @Commit or @Rollback(false) on that method or class.
//
// Subclass and add @AutoConfigureMockMvc for web-layer tests.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
abstract class AbstractIntegrationTest {

    companion object {
        @JvmStatic
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16")
                .withDatabaseName("pollsystem_test")
                .withUsername("test")
                .withPassword("test")
                .also { it.start() }
    }
}
