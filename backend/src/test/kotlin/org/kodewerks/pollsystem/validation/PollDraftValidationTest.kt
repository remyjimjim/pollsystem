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
class PollDraftValidationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var tokens: JwtTokenProvider

    private lateinit var creatorBearer: String

    @BeforeAll
    fun setUp() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "valcreator")
        creatorBearer = "Bearer ${tokens.generateToken(creator.id, creator.email)}"
    }

    private fun validQuestionnaire(): MutableMap<String, Any?> = mutableMapOf(
        "pollTypeId" to 2L,
        "title" to "Title",
        "summary" to "Summary",
        "questions" to listOf(mapOf("text" to "Q1")),
        "zipcodes" to listOf("90001")
    )

    private fun validElection(): MutableMap<String, Any?> = mutableMapOf(
        "pollTypeId" to 1L,
        "title" to "Title",
        "date" to "2030-01-01",
        "zipcode" to "90001",
        "candidates" to listOf(mapOf(
            "name" to "Alice",
            "affiliation" to "Indep",
            "officeName" to "Mayor"
        ))
    )

    private fun validBallotMeasure(): MutableMap<String, Any?> = mutableMapOf(
        "pollTypeId" to 3L,
        "electionId" to 1L,
        "title" to "Bond",
        "summary" to "Approve $1M bond?",
        "effectiveDate" to "2030-01-01"
    )

    @ParameterizedTest(name = "[questionnaire] {0}")
    @MethodSource("invalidQuestionnaires")
    fun `questionnaire draft 400 for invalid payload`(
        @Suppress("UNUSED_PARAMETER") name: String, body: Map<String, Any?>
    ) {
        mockMvc.perform(
            post("/api/polls/questionnaires")
                .header("Authorization", creatorBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @ParameterizedTest(name = "[election] {0}")
    @MethodSource("invalidElections")
    fun `election draft 400 for invalid payload`(
        @Suppress("UNUSED_PARAMETER") name: String, body: Map<String, Any?>
    ) {
        mockMvc.perform(
            post("/api/polls/elections")
                .header("Authorization", creatorBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @ParameterizedTest(name = "[ballot-measure] {0}")
    @MethodSource("invalidBallotMeasures")
    fun `ballot measure draft 400 for invalid payload`(
        @Suppress("UNUSED_PARAMETER") name: String, body: Map<String, Any?>
    ) {
        mockMvc.perform(
            post("/api/polls/ballot-measures")
                .header("Authorization", creatorBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @Suppress("unused")
    private fun invalidQuestionnaires(): List<Arguments> = listOf(
        case("blank title",         validQuestionnaire()) { it["title"] = "" },
        case("missing title",       validQuestionnaire()) { it.remove("title") },
        case("title too long",      validQuestionnaire()) { it["title"] = "x".repeat(501) },
        case("blank summary",       validQuestionnaire()) { it["summary"] = "" },
        case("missing summary",     validQuestionnaire()) { it.remove("summary") },
        case("empty questions",     validQuestionnaire()) { it["questions"] = emptyList<Map<String, Any>>() },
        case("missing questions",   validQuestionnaire()) { it.remove("questions") },
        case("question text blank", validQuestionnaire()) {
            it["questions"] = listOf(mapOf("text" to ""))
        },
        case("question text too long", validQuestionnaire()) {
            it["questions"] = listOf(mapOf("text" to "q".repeat(1001)))
        },
        case("empty zipcodes",      validQuestionnaire()) { it["zipcodes"] = emptyList<String>() },
        case("missing zipcodes",    validQuestionnaire()) { it.remove("zipcodes") }
    )

    @Suppress("unused")
    private fun invalidElections(): List<Arguments> = listOf(
        case("blank title",            validElection()) { it["title"] = "" },
        case("title too long",         validElection()) { it["title"] = "x".repeat(501) },
        case("zipcode 4 digits",       validElection()) { it["zipcode"] = "9000" },
        case("zipcode letters",        validElection()) { it["zipcode"] = "ABCDE" },
        case("blank zipcode",          validElection()) { it["zipcode"] = "" },
        case("candidate blank name",   validElection()) {
            it["candidates"] = listOf(mapOf(
                "name" to "", "affiliation" to "Indep", "officeName" to "Mayor"
            ))
        },
        case("candidate blank affiliation", validElection()) {
            it["candidates"] = listOf(mapOf(
                "name" to "Alice", "affiliation" to "", "officeName" to "Mayor"
            ))
        },
        case("candidate blank officeName", validElection()) {
            it["candidates"] = listOf(mapOf(
                "name" to "Alice", "affiliation" to "Indep", "officeName" to ""
            ))
        },
        case("candidate name too long", validElection()) {
            it["candidates"] = listOf(mapOf(
                "name" to "x".repeat(256), "affiliation" to "Indep", "officeName" to "Mayor"
            ))
        }
    )

    @Suppress("unused")
    private fun invalidBallotMeasures(): List<Arguments> = listOf(
        case("blank title",     validBallotMeasure()) { it["title"] = "" },
        case("title too long",  validBallotMeasure()) { it["title"] = "x".repeat(501) },
        case("blank summary",   validBallotMeasure()) { it["summary"] = "" },
        case("missing title",   validBallotMeasure()) { it.remove("title") },
        case("missing summary", validBallotMeasure()) { it.remove("summary") }
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
