package org.kodewerks.pollsystem.dev

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.poll.QuestionAnswerInput
import org.kodewerks.pollsystem.poll.QuestionInput
import org.kodewerks.pollsystem.poll.QuestionnaireDraftRequest
import org.kodewerks.pollsystem.poll.QuestionnaireResponseController
import org.kodewerks.pollsystem.poll.QuestionnaireResultsController
import org.kodewerks.pollsystem.poll.QuestionnaireService
import org.kodewerks.pollsystem.poll.SubmitResponsesRequest
import org.kodewerks.pollsystem.repository.QuestionRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put

/** The local Dev settings: the results privacy threshold (test default 3). */
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = ["spring.flyway.locations=classpath:db/migration"])
class DevSettingsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var service: QuestionnaireService
    @Autowired private lateinit var questions: QuestionRepository
    @Autowired private lateinit var responses: QuestionnaireResponseController
    @Autowired private lateinit var results: QuestionnaireResultsController
    @Autowired private lateinit var fixtures: TestFixtures

    private fun setThreshold(value: Any?) = mockMvc.put("/api/dev/settings") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(mapOf("resultsThreshold" to value))
    }

    private fun mode(pollId: Long) = results.get(pollId).visibility.mode

    @Test
    fun `changing the threshold applies to results at once, and resetting restores the default`() {
        try {
            mockMvc.get("/api/dev/settings").andExpect {
                jsonPath("$.resultsThreshold") { value(3) }
                jsonPath("$.resultsThresholdOverridden") { value(false) }
            }
            val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "ds-creator")
            val d = service.saveDraft(creator, QuestionnaireDraftRequest(
                pollTypeId = 2L, title = "Threshold", summary = "s", closeDate = null,
                questions = listOf(QuestionInput("Q?")), zipcodes = listOf("90001")))
            service.publish(d.id, creator, confirmed = true)
            val q = questions.findByQuestionnaireId(d.id).single()
            repeat(2) {
                responses.submit(AppUserDetails(fixtures.createUser(emailPrefix = "ds-v")), d.id,
                    SubmitResponsesRequest(listOf(QuestionAnswerInput(q.id, "Yes"))))
            }
            assertThat(mode(d.id)).isEqualTo("HIDDEN")                // 2 < 3

            setThreshold(2).andExpect {
                status { isOk() }
                jsonPath("$.resultsThreshold") { value(2) }
                jsonPath("$.resultsThresholdOverridden") { value(true) }
            }
            assertThat(mode(d.id)).isEqualTo("STEPPED")               // 2 >= 2: shown
            assertThat(results.get(d.id).visibility.minimum).isEqualTo(2)

            setThreshold(null).andExpect { jsonPath("$.resultsThreshold") { value(3) } }
            assertThat(mode(d.id)).isEqualTo("HIDDEN")                // back to 3
        } finally {
            setThreshold(null)
        }
    }

    @Test
    fun `refuses values outside 1 to 100, or not whole`() {
        try {
            for (bad in listOf(0, 101, 2.5, "ten")) setThreshold(bad).andExpect { status { isBadRequest() } }
        } finally {
            setThreshold(null)
        }
    }
}
