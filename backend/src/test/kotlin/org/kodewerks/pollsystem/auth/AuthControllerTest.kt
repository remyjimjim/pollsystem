package org.kodewerks.pollsystem.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.MagicLinkTokenRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `request for an unknown email returns 404 and creates no account`() {
        // Pay-first model: accounts are created only by payment, never by a
        // login attempt. (Provisioning still happens under the local profile
        // for e2e fixtures, but tests run under the test profile.)
        val body = mapOf("email" to "nobody@test.local")
        mockMvc.perform(
            post("/api/auth/magic-link/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isNotFound)

        assertNull(users.findByEmail("nobody@test.local"))
    }

    @Test
    fun `request for an existing email returns 202 and reuses the user`() {
        saveUser("carol@test.local", "+15551234050")
        val body = mapOf("email" to "carol@test.local")

        repeat(2) {
            mockMvc.perform(
                post("/api/auth/magic-link/request")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body))
            ).andExpect(status().isAccepted)
        }

        val matches = users.findAll().filter { it.email == "carol@test.local" }
        assertEquals(1, matches.size)
    }

    @Test
    fun `redeem returns a JWT consumes the token and me echoes the principal`() {
        val user = saveUser("bob@test.local", "+15551234200")
        val rawToken = magicLinks.issueToken(user).token

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
        val rawToken = magicLinks.issueToken(user).token
        val record = tokens.findAll().last { it.userId == user.id }
        tokens.save(record.copy(expiresAt = Instant.now().minus(Duration.ofMinutes(1))))

        mockMvc.perform(
            post("/api/auth/magic-link/redeem")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mapOf("token" to rawToken)))
        ).andExpect(status().isUnauthorized)
    }

    private fun postCode(email: String, code: String) = mockMvc.perform(
        post("/api/auth/magic-link/code")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(mapOf("email" to email, "code" to code)))
    )
    private fun wrongCode(right: String) = "%06d".format((right.toInt() + 1) % 1_000_000)

    @Test
    fun `the emailed code signs in like the link, once, and uses up the link`() {
        val user = saveUser("code-ok@test.local", "+15551234400")
        val issued = magicLinks.issueToken(user)
        val spaced = "${issued.code.take(3)} ${issued.code.drop(3)}"   // as the email shows it

        postCode("Code-OK@test.local", spaced)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.user.email").value("code-ok@test.local"))
        postCode("code-ok@test.local", issued.code).andExpect(status().isUnauthorized)   // once
        mockMvc.perform(
            post("/api/auth/magic-link/redeem")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mapOf("token" to issued.token)))
        ).andExpect(status().isUnauthorized)                                             // same link
    }

    @Test
    fun `five wrong codes retire the code but the link still works`() {
        val user = saveUser("code-wrong@test.local", "+15551234500")
        val issued = magicLinks.issueToken(user)
        repeat(5) { postCode("code-wrong@test.local", wrongCode(issued.code)).andExpect(status().isUnauthorized) }
        postCode("code-wrong@test.local", issued.code).andExpect(status().isUnauthorized)
        mockMvc.perform(
            post("/api/auth/magic-link/redeem")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mapOf("token" to issued.token)))
        ).andExpect(status().isOk)
    }

    @Test
    fun `an expired code, or an unknown email, gets the same 401`() {
        val user = saveUser("code-old@test.local", "+15551234600")
        val issued = magicLinks.issueToken(user)
        val record = tokens.findAll().last { it.userId == user.id }
        tokens.save(record.copy(expiresAt = Instant.now().minus(Duration.ofMinutes(1))))
        postCode("code-old@test.local", issued.code).andExpect(status().isUnauthorized)
        postCode("nobody-code@test.local", "123456").andExpect(status().isUnauthorized)
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
