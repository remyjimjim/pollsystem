package org.kodewerks.pollsystem.stripe

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.security.JwtTokenProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Billing endpoints with Stripe unconfigured (api-key is empty in tests). No
 * Stripe API is contacted: checkout short-circuits to 503 before any network
 * call, and the portal returns 409 for a user who has no Stripe customer yet.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var tokens: JwtTokenProvider

    private lateinit var userBearer: String

    @BeforeAll
    fun setUp() {
        val user = fixtures.createUser(emailPrefix = "billing")
        userBearer = "Bearer ${tokens.generateToken(user.id, user.email)}"
    }

    @Test
    fun `checkout requires authentication`() {
        mockMvc.perform(post("/api/billing/checkout"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `checkout returns 503 when billing is not configured`() {
        mockMvc.perform(post("/api/billing/checkout").header("Authorization", userBearer))
            .andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `portal returns 409 for a user with no Stripe customer`() {
        mockMvc.perform(post("/api/billing/portal").header("Authorization", userBearer))
            .andExpect(status().isConflict)
    }
}
