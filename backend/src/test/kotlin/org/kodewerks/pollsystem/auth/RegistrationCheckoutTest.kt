package org.kodewerks.pollsystem.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * `/api/auth/register-checkout` is the pay-first registration entry point.
 * It validates that email + phone are free and the zipcode is real, then hands
 * back a Stripe Checkout URL. Stripe is unconfigured in tests, so a request that
 * passes validation reaches checkout and returns 503 (no network call is made) —
 * that 503 is the signal that validation passed. Nothing is persisted here.
 */
@AutoConfigureMockMvc
class RegistrationCheckoutTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var fixtures: TestFixtures

    private fun register(email: String, phone: String, zipcode: String) =
        mockMvc.perform(
            post("/api/auth/register-checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mapOf("email" to email, "phone" to phone, "zipcode" to zipcode)))
        )

    @Test
    fun `valid unique registration passes validation and reaches checkout (503, unconfigured)`() {
        register("brandnew@test.local", "+15557770001", "90001")
            .andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `an already-registered email is rejected with 409`() {
        val existing = fixtures.createUser(emailPrefix = "taken")
        register(existing.email, "+15557770002", "90001")
            .andExpect(status().isConflict)
    }

    @Test
    fun `a phone already in use is rejected with 409`() {
        val existing = fixtures.createUser(emailPrefix = "phoneowner")
        register("differentemail@test.local", existing.phone!!, "90001")
            .andExpect(status().isConflict)
    }

    @Test
    fun `an unknown zipcode is rejected with 400`() {
        register("zipless@test.local", "+15557770003", "00000")
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `malformed payload is rejected with 400`() {
        register("not-an-email", "+15557770004", "90001")
            .andExpect(status().isBadRequest)
    }
}
