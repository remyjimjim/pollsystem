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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class PollsKillSwitchTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var flags: GlobalFlagService
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var tokens: JwtTokenProvider

    @Test
    fun `submissions serve when polls are enabled`() {
        // Migration default is polls_disabled = false: reads work regardless.
        mockMvc.perform(get("/api/polls/search"))
            .andExpect(status().isOk)
    }

    @Test
    fun `submissions return 503 when disabled, but reads stay up`() {
        val participant = fixtures.createUser(emailPrefix = "killswitch-participant")
        val bearer = "Bearer ${tokens.generateToken(participant.id, participant.email)}"
        val su = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "killswitch-super")
        flags.setPollsDisabled(true, su.id)

        // A submission (POST to a responses endpoint) is blocked before the
        // controller runs — the poll id need not exist, the interceptor fires first.
        mockMvc.perform(
            post("/api/polls/elections/1/responses")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        ).andExpect(status().isServiceUnavailable)

        // Reads remain available while polls are disabled.
        mockMvc.perform(get("/api/polls/search"))
            .andExpect(status().isOk)
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
