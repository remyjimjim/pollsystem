package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.QuestionRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired

/**
 * The Creator dashboard's "at a glance" figures (creator reports, phase 1):
 * respondents per poll and how many live inside its purview, with the in-area
 * figure withheld when either group is a small one (test threshold: 3).
 */
class CreatorPollsParticipationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: QuestionnaireService
    @Autowired private lateinit var responseController: QuestionnaireResponseController
    @Autowired private lateinit var creatorPolls: CreatorPollsController
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var questions: QuestionRepository

    /** A questionnaire whose purview is ZIP 90001; publish it unless [draft]. */
    private fun questionnaire(creator: User, draft: Boolean = false): Long {
        val d = service.saveDraft(
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = 2L,
                title = "Park hours",
                summary = "Open later?",
                closeDate = null,
                questions = listOf(QuestionInput("Open the park until 10pm?"), QuestionInput("Add lighting?")),
                zipcodes = listOf("90001")
            )
        )
        if (!draft) service.publish(d.id, creator, confirmed = false)
        return d.id
    }

    /** Answers BOTH questions: still one respondent. */
    private fun answer(pollId: Long, zipcode: String, prefix: String) {
        val voter = fixtures.createUser(zipcode = zipcode, emailPrefix = prefix)
        responseController.submit(
            AppUserDetails(voter),
            pollId,
            SubmitResponsesRequest(answers = questions.findByQuestionnaireId(pollId).map { QuestionAnswerInput(it.id, "Yes") })
        )
    }

    private fun row(creator: User, pollId: Long) =
        creatorPolls.list(AppUserDetails(creator), showArchived = false).single { it.id == pollId }

    @Test
    fun `counts each respondent once, and how many are in the poll's area`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = questionnaire(creator)
        repeat(4) { answer(pollId, "90001", "in$it") }
        repeat(3) { answer(pollId, "10001", "out$it") }

        val r = row(creator, pollId)
        assertThat(r.respondents).isEqualTo(7)
        assertThat(r.inArea).isEqualTo(4)
    }

    @Test
    fun `withholds the in-area figure when one group is small`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = questionnaire(creator)
        repeat(4) { answer(pollId, "90001", "in$it") }
        repeat(2) { answer(pollId, "10001", "out$it") } // 2 < threshold 3

        val r = row(creator, pollId)
        assertThat(r.respondents).isEqualTo(6)
        assertThat(r.inArea).isNull()
    }

    @Test
    fun `everyone in the area is fine to show, and drafts count zero`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = questionnaire(creator)
        repeat(3) { answer(pollId, "90001", "in$it") } // outside group empty
        assertThat(row(creator, pollId).let { it.respondents to it.inArea }).isEqualTo(3 to 3)

        val draftId = questionnaire(creator, draft = true)
        val d = row(creator, draftId)
        assertThat(d.status).isEqualTo(PollStatus.DRAFT)
        assertThat(d.respondents to d.inArea).isEqualTo(0 to 0)
    }
}
