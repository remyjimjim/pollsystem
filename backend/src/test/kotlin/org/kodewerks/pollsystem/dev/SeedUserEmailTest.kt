package org.kodewerks.pollsystem.dev

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.repository.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * seed-user's exact `email` option (scripts/seed-demo.bash): a memorable
 * address that reset-test-users with the same prefix removes again. Local
 * profile only, like DevController (see ResetTestUsersTest).
 */
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = ["spring.flyway.locations=classpath:db/migration"])
class SeedUserEmailTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository

    private fun seed(email: String, prefix: String = "zzd-") =
        mockMvc.post("/api/dev/seed-user") { param("emailPrefix", prefix); param("zipcode", "90001"); param("email", email) }

    @Test
    fun `creates exactly that address, and reset with the prefix removes it`() {
        seed("zzd-creator-wash@test.local").andExpect { status { isOk() }; jsonPath("$.email") { value("zzd-creator-wash@test.local") } }
        assertThat(users.findByEmail("zzd-creator-wash@test.local")).isNotNull
        mockMvc.post("/api/dev/reset-test-users") { param("emailPrefix", "zzd-") }.andExpect { status { isOk() } }
        assertThat(users.findByEmail("zzd-creator-wash@test.local")).isNull()
    }

    @Test
    fun `refuses addresses outside test-local or the prefix, and duplicates`() {
        seed("zzd-a@example.com").andExpect { status { is4xxClientError() } }
        seed("other-a@test.local").andExpect { status { is4xxClientError() } }
        seed("zzd-dup@test.local").andExpect { status { isOk() } }
        seed("zzd-dup@test.local").andExpect { status { isConflict() } }
        assertThat(users.findByEmail("zzd-a@example.com")).isNull()
    }
}
