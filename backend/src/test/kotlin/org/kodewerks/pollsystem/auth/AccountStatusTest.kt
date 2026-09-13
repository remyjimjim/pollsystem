package org.kodewerks.pollsystem.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

/**
 * `/api/auth/status` routes the login screen: UNKNOWN → register, LAPSED →
 * sign in then renew, ACTIVE → sign in. It is public (used before any token).
 */
@AutoConfigureMockMvc
class AccountStatusTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var fixtures: TestFixtures

    private fun statusOf(email: String) =
        mockMvc.perform(
            post("/api/auth/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mapOf("email" to email)))
        )

    @Test
    fun `unknown email reports UNKNOWN`() {
        statusOf("ghost@test.local")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UNKNOWN"))
    }

    @Test
    fun `active paid member reports ACTIVE`() {
        val user = fixtures.createUser(emailPrefix = "active") // paidUntil in the future by default
        statusOf(user.email)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    @Test
    fun `USER whose membership expired reports LAPSED`() {
        val user = fixtures.createUser(
            emailPrefix = "lapsed",
            paidUntil = Instant.now().minusSeconds(3600)
        )
        statusOf(user.email)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("LAPSED"))
    }

    @Test
    fun `CREATOR is ACTIVE even without a live subscription (exempt from the gate)`() {
        val creator = fixtures.createUser(
            access = AccessLevel.CREATOR,
            emailPrefix = "creator",
            paidUntil = null
        )
        statusOf(creator.email)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    @Test
    fun `email lookup is case-insensitive`() {
        val user = fixtures.createUser(emailPrefix = "mixedcase")
        statusOf(user.email.uppercase())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }
}
