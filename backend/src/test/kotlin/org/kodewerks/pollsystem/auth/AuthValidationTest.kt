package org.kodewerks.pollsystem.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Bean Validation (`@Valid`) coverage for the auth DTOs. These exercise
 * Spring's MethodArgumentNotValidException path, which returns 400.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthValidationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper

    private fun validRequest(): MutableMap<String, Any?> = mutableMapOf(
        "email" to "valid@test.local",
        "phone" to "+15551234567",
        "zipcode" to "90001"
    )

    private fun validRedeem(): MutableMap<String, Any?> = mutableMapOf(
        "token" to "abcdef0123456789"
    )

    @ParameterizedTest(name = "[request] {0}")
    @MethodSource("invalidRequestPayloads")
    fun `request returns 400 for invalid payload`(@Suppress("UNUSED_PARAMETER") name: String, body: Map<String, Any?>) {
        mockMvc.perform(
            post("/api/auth/magic-link/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @ParameterizedTest(name = "[redeem] {0}")
    @MethodSource("invalidRedeemPayloads")
    fun `redeem returns 400 for invalid payload`(@Suppress("UNUSED_PARAMETER") name: String, body: Map<String, Any?>) {
        mockMvc.perform(
            post("/api/auth/magic-link/redeem")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @Suppress("unused")
    private fun invalidRequestPayloads(): List<Arguments> = listOf(
        request("malformed email")    { it["email"] = "not-an-email" },
        request("blank email")        { it["email"] = "" },
        request("missing email")      { it.remove("email") },
        request("phone too short")    { it["phone"] = "12345" },
        request("phone with letters") { it["phone"] = "555-CALL-NOW" },
        request("phone too long")     { it["phone"] = "1".repeat(21) },
        request("blank phone")        { it["phone"] = "" },
        request("zipcode 4 digits")   { it["zipcode"] = "9000" },
        request("zipcode 6 digits")   { it["zipcode"] = "900012" },
        request("zipcode letters")    { it["zipcode"] = "ABCDE" },
        request("blank zipcode")      { it["zipcode"] = "" }
    )

    @Suppress("unused")
    private fun invalidRedeemPayloads(): List<Arguments> = listOf(
        redeem("blank token")   { it["token"] = "" },
        redeem("missing token") { it.remove("token") }
    )

    private fun request(name: String, modify: (MutableMap<String, Any?>) -> Unit): Arguments {
        val body = validRequest().also(modify)
        return Arguments.of(name, body)
    }

    private fun redeem(name: String, modify: (MutableMap<String, Any?>) -> Unit): Arguments {
        val body = validRedeem().also(modify)
        return Arguments.of(name, body)
    }
}
