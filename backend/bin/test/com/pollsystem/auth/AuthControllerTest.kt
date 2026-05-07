package com.pollsystem.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.pollsystem.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class AuthControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper

    @Test
    fun `register issues a token and creates a USER`() {
        val body = mapOf(
            "email" to "alice@test.local",
            "phone" to "+15551234001",
            "zipcode" to "90001",
            "passcode" to "password123"
        )

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.user.email").value("alice@test.local"))
            .andExpect(jsonPath("$.user.access").value("USER"))
            .andExpect(jsonPath("$.user.zipcode").value("90001"))
    }

    @Test
    fun `register rejects duplicate email`() {
        val body = mapOf(
            "email" to "dup@test.local",
            "phone" to "+15551234100",
            "zipcode" to "90001",
            "passcode" to "password123"
        )
        mockMvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isCreated)

        // second registration with same email
        val collide = body + ("phone" to "+15551234101")
        mockMvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(collide))
        ).andExpect(status().isConflict)
    }

    @Test
    fun `login returns a token and me echoes the principal`() {
        val email = "bob@test.local"
        val password = "password123"
        val register = mapOf(
            "email" to email,
            "phone" to "+15551234200",
            "zipcode" to "90001",
            "passcode" to password
        )
        mockMvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(register))
        ).andExpect(status().isCreated)

        val loginResp = mockMvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mapOf("email" to email, "passcode" to password)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andReturn()
            .response.contentAsString
        val token = json.readTree(loginResp).get("token").asText()

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(email))
    }

    @Test
    fun `me without token returns 401`() {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `login with wrong password returns 401`() {
        val email = "wrong@test.local"
        mockMvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mapOf(
                    "email" to email,
                    "phone" to "+15551234300",
                    "zipcode" to "90001",
                    "passcode" to "rightpass1"
                )))
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mapOf(
                    "email" to email,
                    "passcode" to "wrongpass1"
                )))
        ).andExpect(status().isUnauthorized)
    }
}
