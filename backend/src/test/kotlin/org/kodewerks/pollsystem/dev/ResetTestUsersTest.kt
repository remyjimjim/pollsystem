package org.kodewerks.pollsystem.dev

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * `POST /api/dev/reset-test-users` (local profile only) must clear a test
 * user's ballot measures before their elections: a measure hangs off an
 * election (FK), and deleting elections first failed with a 500 once an e2e
 * spec attached a measure to an election — breaking every later reset in CI.
 *
 * Runs with the `local` profile on top of `test` (DevController is
 * local-only), keeping the test Flyway locations so the local seed data
 * doesn't load into the shared test database.
 */
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = ["spring.flyway.locations=classpath:db/migration"])
class ResetTestUsersTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var em: EntityManager

    private fun count(sql: String): Long = (em.createNativeQuery(sql).singleResult as Number).toLong()

    @Test
    fun `reset removes a test user's ballot measure and the election it hangs off`() {
        val seeded = objectMapper.readTree(
            mockMvc.post("/api/dev/seed-ballot-measure") { param("emailPrefix", "zzr") }
                .andExpect { status { isOk() } }
                .andReturn().response.contentAsString
        )
        val measureId = seeded["id"].asLong()
        val electionId = seeded["electionId"].asLong()
        assertThat(count("SELECT count(*) FROM ballot_measures WHERE election_id = $electionId")).isEqualTo(1)

        mockMvc.post("/api/dev/reset-test-users") { param("emailPrefix", "zzr") }
            .andExpect { status { isOk() } }
        em.clear()

        assertThat(count("SELECT count(*) FROM ballot_measures WHERE id = $measureId")).isZero()
        assertThat(count("SELECT count(*) FROM elections WHERE id = $electionId")).isZero()
        assertThat(count(
            "SELECT count(*) FROM poll_purviews WHERE (poll_type = 'BALLOT_MEASURE' AND poll_id = $measureId)" +
                " OR (poll_type = 'ELECTION' AND poll_id = $electionId)"
        )).isZero()
        assertThat(count("SELECT count(*) FROM users WHERE email LIKE 'zzr%'")).isZero()
    }
}
