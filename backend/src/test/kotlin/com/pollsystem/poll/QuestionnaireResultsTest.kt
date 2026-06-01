package com.pollsystem.poll

import com.pollsystem.AbstractIntegrationTest
import com.pollsystem.TestFixtures
import com.pollsystem.model.AccessLevel
import com.pollsystem.model.User
import com.pollsystem.repository.QuestionRepository
import com.pollsystem.security.AppUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Note: test profile sets `app.results.k-anonymity-threshold = 3` so we only
 * need 3 responders in a filter group to clear suppression.
 */
class QuestionnaireResultsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: QuestionnaireService
    @Autowired private lateinit var responseController: QuestionnaireResponseController
    @Autowired private lateinit var resultsController: QuestionnaireResultsController
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var questions: QuestionRepository

    private val pollTypeId = 2L

    private fun publishOneQuestionPoll(creator: User): Long {
        val draft = service.saveDraft(
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = pollTypeId,
                title = "Public art",
                summary = "Should we fund it?",
                closeDate = null,
                questions = listOf(QuestionInput("Should we fund public art?")),
                zipcodes = listOf("90001")
            )
        )
        service.publish(draft.id, creator, confirmed = false)
        return draft.id
    }

    private fun submit(pollId: Long, voter: User, response: String) {
        val q = questions.findByQuestionnaireId(pollId).first()
        responseController.submit(
            AppUserDetails(voter),
            pollId,
            SubmitResponsesRequest(answers = listOf(QuestionAnswerInput(q.id, response)))
        )
    }

    @Test
    fun `aggregates count answers case-insensitively`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publishOneQuestionPoll(creator)

        repeat(3) { submit(pollId, fixtures.createUser(emailPrefix = "v$it"), "Yes") }
        repeat(2) { submit(pollId, fixtures.createUser(emailPrefix = "n$it"), "no") }
        // Mixed case: should fold into "yes" bucket
        submit(pollId, fixtures.createUser(emailPrefix = "mixed"), "YES")

        val results = resultsController.get(pollId, zipcode = null)

        assertThat(results.suppressed).isFalse
        assertThat(results.totalRespondents).isEqualTo(6)
        val first = results.perQuestion.first()
        assertThat(first.totalResponses).isEqualTo(6)
        assertThat(first.byAnswer["yes"]).isEqualTo(4)
        assertThat(first.byAnswer["no"]).isEqualTo(2)
    }

    @Test
    fun `zipcode filter below threshold suppresses results`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publishOneQuestionPoll(creator)

        // 2 voters at 90001, 5 at 90012 — filter to 90001 should suppress (<3)
        repeat(2) { submit(pollId, fixtures.createUser(zipcode = "90001", emailPrefix = "a$it"), "Yes") }
        repeat(5) { submit(pollId, fixtures.createUser(zipcode = "90012", emailPrefix = "b$it"), "Yes") }

        val filtered = resultsController.get(pollId, zipcode = "90001")
        assertThat(filtered.suppressed).isTrue
        assertThat(filtered.suppressionMessage).isNotBlank
        assertThat(filtered.perQuestion).isEmpty()

        // Without the filter, all 7 contribute and suppression doesn't trigger
        val unfiltered = resultsController.get(pollId, zipcode = null)
        assertThat(unfiltered.suppressed).isFalse
        assertThat(unfiltered.totalRespondents).isEqualTo(7)
    }

    @Test
    fun `onlyPurview narrows to submitters within the poll's zipcode set`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publishOneQuestionPoll(creator)  // domain zip = 90001

        repeat(3) { submit(pollId, fixtures.createUser(zipcode = "90001", emailPrefix = "in$it"), "Yes") }
        repeat(2) { submit(pollId, fixtures.createUser(zipcode = "10001", emailPrefix = "out$it"), "No") }

        val all = resultsController.get(pollId, zipcode = null, onlyPurview = false)
        assertThat(all.totalRespondents).isEqualTo(5)

        val withinPurview = resultsController.get(pollId, zipcode = null, onlyPurview = true)
        assertThat(withinPurview.suppressed).isFalse
        assertThat(withinPurview.totalRespondents).isEqualTo(3)
        assertThat(withinPurview.filterApplied).containsEntry("onlyPurview", "true")
    }

    @Test
    fun `zipcode filter at or above threshold returns aggregates`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publishOneQuestionPoll(creator)

        // Exactly threshold (3) at 90001, plus noise elsewhere
        repeat(3) { submit(pollId, fixtures.createUser(zipcode = "90001", emailPrefix = "a$it"), "Yes") }
        repeat(2) { submit(pollId, fixtures.createUser(zipcode = "90012", emailPrefix = "b$it"), "No") }

        val filtered = resultsController.get(pollId, zipcode = "90001")
        assertThat(filtered.suppressed).isFalse
        assertThat(filtered.totalRespondents).isEqualTo(3)
        assertThat(filtered.perQuestion.first().byAnswer["yes"]).isEqualTo(3)
        assertThat(filtered.perQuestion.first().byAnswer["no"]).isNull()
    }
}
