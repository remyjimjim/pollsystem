package org.kodewerks.pollsystem.superadmin

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.security.JwtTokenProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class PollsKillSwitchTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var flags: GlobalFlagService
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var tokens: JwtTokenProvider

    @Test
    fun `public poll endpoint serves when polls are enabled`() {
        // Migration default is polls_disabled = false.
        mockMvc.perform(get("/api/polls/search"))
            .andExpect(status().isOk)
    }

    @Test
    fun `public poll endpoint returns 503 when polls are disabled`() {
        val su = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "killswitch-super")
        flags.setPollsDisabled(true, su.id)

        mockMvc.perform(get("/api/polls/search"))
            .andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `super toggles the flag and the super namespace stays reachable while disabled`() {
        val su = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "killswitch-toggle")
        val bearer = "Bearer ${tokens.generateToken(su.id, su.email)}"

        mockMvc.perform(
            put("/api/super/flags/polls-disabled")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"disabled":true}""")
        ).andExpect(status().isOk)
        assertThat(flags.pollsDisabled()).isTrue()

        // /api/super/** is outside the /api/polls/** interceptor, so it still works.
        mockMvc.perform(get("/api/super/flags").header("Authorization", bearer))
            .andExpect(status().isOk)

        // Turn it back off.
        mockMvc.perform(
            put("/api/super/flags/polls-disabled")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"disabled":false}""")
        ).andExpect(status().isOk)
        assertThat(flags.pollsDisabled()).isFalse()
    }
}
