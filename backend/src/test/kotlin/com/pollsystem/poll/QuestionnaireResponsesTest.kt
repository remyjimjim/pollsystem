package com.pollsystem.poll

import com.pollsystem.AbstractIntegrationTest
import com.pollsystem.TestFixtures
import com.pollsystem.model.AccessLevel
import com.pollsystem.model.User
import com.pollsystem.repository.QuestionRepository
import com.pollsystem.repository.QuestionResponseRepository
import com.pollsystem.security.AppUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException

class QuestionnaireResponsesTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: QuestionnaireService
    @Autowired private lateinit var controller: QuestionnaireResponseController
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var questions: QuestionRepository
    @Autowired private lateinit var responses: QuestionResponseRepository

    private val pollTypeId = 2L  // Questionnaire

    private fun publishQuestionnaire(creator: User, questionTexts: List<String>): Long {
        val draft = service.saveDraft(
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = pollTypeId,
                title = "Test poll",
                summary = "summary",
                closeDate = null,
                questions = questionTexts.map { QuestionInput(it) },
                zipcodes = listOf("90001")
            )
        )
        service.publish(draft.id, creator, confirmed = false)
        return draft.id
    }

    @Test
    fun `first submit creates one response per question`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publishQuestionnaire(creator, listOf("Q1", "Q2"))
        val voter = fixtures.createUser(emailPrefix = "voter")

        val qList = questions.findByQuestionnaireId(pollId)
        val result = controller.submit(
            AppUserDetails(voter),
            pollId,
            SubmitResponsesRequest(answers = qList.map {
                QuestionAnswerInput(it.id, "Yes")
            })
        )

        assertThat(result.hasResponses).isTrue
        assertThat(result.responses).hasSize(2)
        assertThat(responses.findByQuestionnaireIdAndUserId(pollId, voter.id)).hasSize(2)
    }

    @Test
    fun `second submit by the same user updates existing responses (not duplicate insert)`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publishQuestionnaire(creator, listOf("Q1"))
        val voter = fixtures.createUser(emailPrefix = "voter")
        val qList = questions.findByQuestionnaireId(pollId)

        controller.submit(
            AppUserDetails(voter),
            pollId,
            SubmitResponsesRequest(answers = listOf(QuestionAnswerInput(qList[0].id, "Yes")))
        )
        controller.submit(
            AppUserDetails(voter),
            pollId,
            SubmitResponsesRequest(answers = listOf(QuestionAnswerInput(qList[0].id, "No", "changed mind")))
        )

        val rows = responses.findByQuestionnaireIdAndUserId(pollId, voter.id)
        assertThat(rows).hasSize(1)
        assertThat(rows[0].response).isEqualTo("No")
        assertThat(rows[0].comment).isEqualTo("changed mind")
        assertThat(rows[0].lastModified).isNotNull
    }

    @Test
    fun `submit with missing answers returns 400`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publishQuestionnaire(creator, listOf("Q1", "Q2"))
        val voter = fixtures.createUser(emailPrefix = "voter")
        val qList = questions.findByQuestionnaireId(pollId)

        assertThatThrownBy {
            controller.submit(
                AppUserDetails(voter),
                pollId,
                SubmitResponsesRequest(answers = listOf(QuestionAnswerInput(qList[0].id, "Yes")))
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(400)
            assertThat(it.reason).contains("Missing answers")
        }
    }

    @Test
    fun `submit with unknown questionId returns 400`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publishQuestionnaire(creator, listOf("Q1"))
        val voter = fixtures.createUser(emailPrefix = "voter")

        assertThatThrownBy {
            controller.submit(
                AppUserDetails(voter),
                pollId,
                SubmitResponsesRequest(answers = listOf(QuestionAnswerInput(999_999L, "Yes")))
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(400)
            assertThat(it.reason).contains("Unknown")
        }
    }

    @Test
    fun `submit on a DRAFT questionnaire is rejected`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val draft = service.saveDraft(
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = pollTypeId,
                title = "Draft only",
                summary = "summary",
                closeDate = null,
                questions = listOf(QuestionInput("Q1")),
                zipcodes = listOf("90001")
            )
        )
        val voter = fixtures.createUser(emailPrefix = "voter")
        val qList = questions.findByQuestionnaireId(draft.id)

        assertThatThrownBy {
            controller.submit(
                AppUserDetails(voter),
                draft.id,
                SubmitResponsesRequest(answers = listOf(QuestionAnswerInput(qList[0].id, "Yes")))
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(409)
        }
    }
}
