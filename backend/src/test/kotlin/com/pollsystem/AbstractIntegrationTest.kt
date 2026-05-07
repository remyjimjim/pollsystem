package com.pollsystem

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Boots the Spring context against a fresh Postgres container per test class.
 * Flyway migrations V1-V6 run on startup, so seeded states/counties/zips/poll-types
 * are available to every test.
 *
 * Subclass and add @AutoConfigureMockMvc for web-layer tests.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
abstract class AbstractIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("pollsystem_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
    }
}
