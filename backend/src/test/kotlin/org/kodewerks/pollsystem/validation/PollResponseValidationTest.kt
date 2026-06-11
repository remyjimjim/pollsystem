package org.kodewerks.pollsystem.validation

import com.fasterxml.jackson.databind.ObjectMapper
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
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

/**
 * Validation runs before the controller method body, so it doesn't matter
 * that the {id} path-param doesn't reference a real poll. Spring rejects
 * with 400 before the service ever looks the row up.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PollResponseValidationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var tokens: JwtTokenProvider

    private lateinit var userBearer: String

    @BeforeAll
    fun setUp() {
        val user = fixtures.createUser(emailPrefix = "valresp")
        userBearer = "Bearer ${tokens.generateToken(user.id, user.email)}"
    }

    @ParameterizedTest(name = "[questionnaire response] {0}")
    @MethodSource("invalidQuestionnaireResponses")
    fun `questionnaire submit 400 for invalid payload`(
        @Suppress("UNUSED_PARAMETER") name: String, body: Map<String, Any?>
    ) {
        mockMvc.perform(
            post("/api/polls/questionnaires/1/responses")
                .header("Authorization", userBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @ParameterizedTest(name = "[election response] {0}")
    @MethodSource("invalidElectionResponses")
    fun `election submit 400 for invalid payload`(
        @Suppress("UNUSED_PARAMETER") name: String, body: Map<String, Any?>
    ) {
        mockMvc.perform(
            post("/api/polls/elections/1/responses")
                .header("Authorization", userBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @ParameterizedTest(name = "[ballot-measure response] {0}")
    @MethodSource("invalidBallotResponses")
    fun `ballot measure submit 400 for invalid payload`(
        @Suppress("UNUSED_PARAMETER") name: String, body: Map<String, Any?>
    ) {
        mockMvc.perform(
            post("/api/polls/ballot-measures/1/responses")
                .header("Authorization", userBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @Suppress("unused")
    private fun invalidQuestionnaireResponses(): List<Arguments> = listOf(
        Arguments.of("empty answers list",
            mapOf("answers" to emptyList<Map<String, Any>>())),
        Arguments.of("missing answers field",
            emptyMap<String, Any>()),
        Arguments.of("answer with blank response",
            mapOf("answers" to listOf(mapOf("questionId" to 1, "response" to "")))),
        Arguments.of("answer missing response field",
            mapOf("answers" to listOf(mapOf("questionId" to 1))))
    )

    @Suppress("unused")
    private fun invalidElectionResponses(): List<Arguments> = listOf(
        Arguments.of("empty answers list",
            mapOf("answers" to emptyList<Map<String, Any>>())),
        Arguments.of("missing answers field",
            emptyMap<String, Any>())
    )

    @Suppress("unused")
    private fun invalidBallotResponses(): List<Arguments> = listOf(
        Arguments.of("missing response field",
            emptyMap<String, Any>()),
        Arguments.of("response is null",
            mapOf("response" to null))
    )
}
