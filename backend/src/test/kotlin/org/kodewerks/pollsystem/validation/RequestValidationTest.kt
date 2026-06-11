package org.kodewerks.pollsystem.validation

import com.fasterxml.jackson.databind.ObjectMapper
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.security.JwtTokenProvider
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
class RequestValidationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var tokens: JwtTokenProvider

    private lateinit var userBearer: String
    private lateinit var adminBearer: String
    private lateinit var superBearer: String

    @BeforeAll
    fun setUp() {
        val user = fixtures.createUser(emailPrefix = "valuser")
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "valadmin")
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "valsup")
        userBearer = "Bearer ${tokens.generateToken(user.id, user.email)}"
        adminBearer = "Bearer ${tokens.generateToken(admin.id, admin.email)}"
        superBearer = "Bearer ${tokens.generateToken(sup.id, sup.email)}"
    }

    private fun validCreatorRequest(): MutableMap<String, Any?> = mutableMapOf(
        "pollTypeIds" to listOf(1L),
        "zipcodes" to listOf("90001"),
        "reason" to "Reason"
    )

    private fun validAdminRequest(): MutableMap<String, Any?> = mutableMapOf(
        "zipcodes" to listOf("90001"),
        "reason" to "Reason"
    )

    @ParameterizedTest(name = "[creator-request] {0}")
    @MethodSource("invalidCreatorRequests")
    fun `submit creator request 400 for invalid payload`(
        @Suppress("UNUSED_PARAMETER") name: String, body: Map<String, Any?>
    ) {
        mockMvc.perform(
            post("/api/creator-requests")
                .header("Authorization", userBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @ParameterizedTest(name = "[admin-request] {0}")
    @MethodSource("invalidAdminRequests")
    fun `submit admin request 400 for invalid payload`(
        @Suppress("UNUSED_PARAMETER") name: String, body: Map<String, Any?>
    ) {
        mockMvc.perform(
            post("/api/admin-requests")
                .header("Authorization", userBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @ParameterizedTest(name = "[creator-request batch-approve] {0}")
    @MethodSource("invalidBatchPayloads")
    fun `creator-request batch-approve 400 for invalid payload`(
        @Suppress("UNUSED_PARAMETER") name: String, body: Map<String, Any?>
    ) {
        mockMvc.perform(
            post("/api/admin/creator-requests/batch-approve")
                .header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @ParameterizedTest(name = "[admin-request batch-approve] {0}")
    @MethodSource("invalidBatchPayloads")
    fun `admin-request batch-approve 400 for invalid payload`(
        @Suppress("UNUSED_PARAMETER") name: String, body: Map<String, Any?>
    ) {
        mockMvc.perform(
            post("/api/super/admin-requests/batch-approve")
                .header("Authorization", superBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @Suppress("unused")
    private fun invalidCreatorRequests(): List<Arguments> = listOf(
        case("empty pollTypeIds", validCreatorRequest()) { it["pollTypeIds"] = emptyList<Long>() },
        case("missing pollTypeIds", validCreatorRequest()) { it.remove("pollTypeIds") },
        case("empty zipcodes",     validCreatorRequest()) { it["zipcodes"] = emptyList<String>() },
        case("missing zipcodes",   validCreatorRequest()) { it.remove("zipcodes") },
        // Reason is optional now; only "too long" remains a 400.
        case("reason too long",    validCreatorRequest()) { it["reason"] = "x".repeat(2001) }
    )

    @Suppress("unused")
    private fun invalidAdminRequests(): List<Arguments> = listOf(
        case("empty zipcodes",   validAdminRequest()) { it["zipcodes"] = emptyList<String>() },
        case("missing zipcodes", validAdminRequest()) { it.remove("zipcodes") },
        // Reason is optional now; only "too long" remains a 400.
        case("reason too long",  validAdminRequest()) { it["reason"] = "x".repeat(2001) }
    )

    @Suppress("unused")
    private fun invalidBatchPayloads(): List<Arguments> = listOf(
        case("empty requestIds",   mutableMapOf("requestIds" to emptyList<Long>())) { },
        case("missing requestIds", mutableMapOf<String, Any?>()) { }
    )

    private fun case(
        name: String,
        body: MutableMap<String, Any?>,
        modify: (MutableMap<String, Any?>) -> Unit
    ): Arguments {
        modify(body)
        return Arguments.of(name, body)
    }
}
