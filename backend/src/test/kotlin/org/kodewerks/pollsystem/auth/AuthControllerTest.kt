package org.kodewerks.pollsystem.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.MagicLinkTokenRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant

@AutoConfigureMockMvc
class AuthControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var magicLinks: MagicLinkService
    @Autowired private lateinit var tokens: MagicLinkTokenRepository

    @Test
    fun `request creates a new USER and returns 202`() {
        val body = mapOf(
            "email" to "alice@test.local",
            "phone" to "+15551234001",
            "zipcode" to "90001"
        )
        mockMvc.perform(
            post("/api/auth/magic-link/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isAccepted)

        val created = users.findByEmail("alice@test.local")
        assertNotNull(created)
        assertEquals("90001", created!!.zipcode)
    }

    @Test
    fun `request for existing email reuses the user`() {
        val body = mapOf(
            "email" to "carol@test.local",
            "phone" to "+15551234050",
            "zipcode" to "90001"
        )
        mockMvc.perform(
            post("/api/auth/magic-link/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isAccepted)

        // Same email, different phone — should NOT create a second user
        val again = body + ("phone" to "+15551234051")
        mockMvc.perform(
            post("/api/auth/magic-link/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(again))
        ).andExpect(status().isAccepted)

        val matches = users.findAll().filter { it.email == "carol@test.local" }
        assertEquals(1, matches.size)
    }

    @Test
    fun `request rejects new email with phone already used by another account`() {
        val first = mapOf(
            "email" to "first@test.local",
            "phone" to "+15551234100",
            "zipcode" to "90001"
        )
        mockMvc.perform(
            post("/api/auth/magic-link/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(first))
        ).andExpect(status().isAccepted)

        val collide = mapOf(
            "email" to "second@test.local",
            "phone" to "+15551234100", // same phone, different email -> conflict
            "zipcode" to "90001"
        )
        mockMvc.perform(
            post("/api/auth/magic-link/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(collide))
        ).andExpect(status().isConflict)
    }

    @Test
    fun `redeem returns a JWT consumes the token and me echoes the principal`() {
        val user = saveUser("bob@test.local", "+15551234200")
        val rawToken = magicLinks.issueToken(user)

        val resp = mockMvc.perform(
            post("/api/auth/magic-link/redeem")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mapOf("token" to rawToken)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.user.email").value("bob@test.local"))
            .andReturn()
            .response.contentAsString

        val jwt = json.readTree(resp).get("token").asText()
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer $jwt"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("bob@test.local"))

        // Replay the same magic-link — must fail.
        mockMvc.perform(
            post("/api/auth/magic-link/redeem")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mapOf("token" to rawToken)))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `redeem rejects unknown token`() {
        mockMvc.perform(
            post("/api/auth/magic-link/redeem")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mapOf("token" to "deadbeef".repeat(8))))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `redeem rejects expired token`() {
        val user = saveUser("dave@test.local", "+15551234300")
        // Issue normally, then rewind expiry past now.
        val rawToken = magicLinks.issueToken(user)
        val record = tokens.findAll().last { it.userId == user.id }
        tokens.save(record.copy(expiresAt = Instant.now().minus(Duration.ofMinutes(1))))

        mockMvc.perform(
            post("/api/auth/magic-link/redeem")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mapOf("token" to rawToken)))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `me without token returns 401`() {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized)
    }

    private fun saveUser(email: String, phone: String): User =
        users.save(
            User(
                email = email,
                phone = phone,
                zipcode = "90001",
                isEnabled = true
            )
        )
}
