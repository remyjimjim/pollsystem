package com.pollsystem.validation

import com.fasterxml.jackson.databind.ObjectMapper
import com.pollsystem.AbstractIntegrationTest
import com.pollsystem.TestFixtures
import com.pollsystem.model.AccessLevel
import com.pollsystem.security.JwtTokenProvider
import org.junit.jupiter.api.BeforeAll
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

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IpRuleValidationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var tokens: JwtTokenProvider

    private lateinit var superBearer: String

    @BeforeAll
    fun setUp() {
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "valip")
        superBearer = "Bearer ${tokens.generateToken(sup.id, sup.email)}"
    }

    @ParameterizedTest(name = "[ip-rule] {0}")
    @MethodSource("invalidIpRules")
    fun `ip rule create 400 for invalid payload`(
        @Suppress("UNUSED_PARAMETER") name: String, body: Map<String, Any?>
    ) {
        mockMvc.perform(
            post("/api/super/ip-rules")
                .header("Authorization", superBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @Suppress("unused")
    private fun invalidIpRules(): List<Arguments> = listOf(
        // Bean Validation cases (rejected by @Valid)
        Arguments.of("blank value",
            mapOf("value" to "", "type" to "ALLOW")),
        Arguments.of("missing value field",
            mapOf("type" to "ALLOW")),
        Arguments.of("value too long",
            mapOf("value" to "x".repeat(65), "type" to "ALLOW")),
        Arguments.of("missing type field",
            mapOf("value" to "10.0.0.0/8")),
        Arguments.of("unknown enum type",
            mapOf("value" to "10.0.0.0/8", "type" to "MAYBE")),

        // Service-level syntactic validation cases (still 400)
        Arguments.of("garbage value",
            mapOf("value" to "not-an-ip", "type" to "ALLOW")),
        Arguments.of("value with letters",
            mapOf("value" to "10.0.0.x", "type" to "ALLOW"))
    )
}
