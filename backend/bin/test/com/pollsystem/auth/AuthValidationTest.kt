package com.pollsystem.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.pollsystem.AbstractIntegrationTest
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

    private fun validRegister(): MutableMap<String, Any?> = mutableMapOf(
        "email" to "valid@test.local",
        "phone" to "+15551234567",
        "zipcode" to "90001",
        "passcode" to "password123"
    )

    private fun validLogin(): MutableMap<String, Any?> = mutableMapOf(
        "email" to "valid@test.local",
        "passcode" to "password123"
    )

    @ParameterizedTest(name = "[register] {0}")
    @MethodSource("invalidRegisterPayloads")
    fun `register returns 400 for invalid payload`(@Suppress("UNUSED_PARAMETER") name: String, body: Map<String, Any?>) {
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @ParameterizedTest(name = "[login] {0}")
    @MethodSource("invalidLoginPayloads")
    fun `login returns 400 for invalid payload`(@Suppress("UNUSED_PARAMETER") name: String, body: Map<String, Any?>) {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @Suppress("unused")
    private fun invalidRegisterPayloads(): List<Arguments> = listOf(
        register("malformed email")    { it["email"] = "not-an-email" },
        register("blank email")        { it["email"] = "" },
        register("missing email")      { it.remove("email") },
        register("phone too short")    { it["phone"] = "12345" },
        register("phone with letters") { it["phone"] = "555-CALL-NOW" },
        register("phone too long")     { it["phone"] = "1".repeat(21) },
        register("blank phone")        { it["phone"] = "" },
        register("zipcode 4 digits")   { it["zipcode"] = "9000" },
        register("zipcode 6 digits")   { it["zipcode"] = "900012" },
        register("zipcode letters")    { it["zipcode"] = "ABCDE" },
        register("blank zipcode")      { it["zipcode"] = "" },
        register("passcode too short") { it["passcode"] = "short" },
        register("passcode too long")  { it["passcode"] = "x".repeat(101) },
        register("blank passcode")     { it["passcode"] = "" }
    )

    @Suppress("unused")
    private fun invalidLoginPayloads(): List<Arguments> = listOf(
        login("malformed email")  { it["email"] = "not-an-email" },
        login("blank email")      { it["email"] = "" },
        login("missing email")    { it.remove("email") },
        login("blank passcode")   { it["passcode"] = "" },
        login("missing passcode") { it.remove("passcode") }
    )

    private fun register(name: String, modify: (MutableMap<String, Any?>) -> Unit): Arguments {
        val body = validRegister().also(modify)
        return Arguments.of(name, body)
    }

    private fun login(name: String, modify: (MutableMap<String, Any?>) -> Unit): Arguments {
        val body = validLogin().also(modify)
        return Arguments.of(name, body)
    }
}
