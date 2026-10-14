package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.QuestionRepository
import org.kodewerks.pollsystem.security.AppUserDetails
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

    /** Supers see live, raw results; everyone else sees the stepped snapshot as percentages. */
    private val sup by lazy { AppUserDetails(fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "res-super")) }

    @Test
    fun `aggregates count answers case-insensitively`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publishOneQuestionPoll(creator)

        repeat(3) { submit(pollId, fixtures.createUser(emailPrefix = "v$it"), "Yes") }
        repeat(2) { submit(pollId, fixtures.createUser(emailPrefix = "n$it"), "no") }
        // Mixed case: should fold into "yes" bucket
        submit(pollId, fixtures.createUser(emailPrefix = "mixed"), "YES")

        val raw = resultsController.get(pollId, principal = sup)
        assertThat(raw.visibility.mode).isEqualTo("LIVE")
        assertThat(raw.totalRespondents).isEqualTo(6)
        assertThat(raw.perQuestion.first().byAnswer).isEqualTo(mapOf("yes" to 4, "no" to 2))

        // The public sees rounded percentages only (6 respondents = a step of 3).
        val public = resultsController.get(pollId)
        assertThat(public.visibility.mode).isEqualTo("STEPPED")
        assertThat(public.totalRespondents).isNull()
        assertThat(public.perQuestion.first().byAnswer).isNull()
        assertThat(public.perQuestion.first().byAnswerPct).isEqualTo(mapOf("yes" to 67, "no" to 33))
    }

    @Test
    fun `zipcode filter below threshold suppresses results`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publishOneQuestionPoll(creator)

        // 2 voters at 90001, 4 at 90012 — filter to 90001 should suppress (<3)
        repeat(2) { submit(pollId, fixtures.createUser(zipcode = "90001", emailPrefix = "a$it"), "Yes") }
        repeat(4) { submit(pollId, fixtures.createUser(zipcode = "90012", emailPrefix = "b$it"), "Yes") }

        val filtered = resultsController.get(pollId, zipcodes = listOf("90001"))
        assertThat(filtered.suppressed).isTrue
        assertThat(filtered.suppressionMessage).isNotBlank
        assertThat(filtered.perQuestion).isEmpty()

        // Without the filter, suppression doesn't trigger
        assertThat(resultsController.get(pollId).suppressed).isFalse
        // A super sees the small group anyway (raw numbers at all times).
        assertThat(resultsController.get(pollId, zipcodes = listOf("90001"), principal = sup).totalRespondents).isEqualTo(2)
    }

    @Test
    fun `within-outside purview split narrows to the selected group`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publishOneQuestionPoll(creator)  // purview mirrored to ZIP 90001

        repeat(3) { submit(pollId, fixtures.createUser(zipcode = "90001", emailPrefix = "in$it"), "Yes") }
        repeat(3) { submit(pollId, fixtures.createUser(zipcode = "10001", emailPrefix = "out$it"), "No") }

        // Within-purview only → the 3 at 90001 (>= threshold 3, not suppressed).
        val within = resultsController.get(pollId, withinPurview = true, outsidePurview = false)
        assertThat(within.suppressed).isFalse
        assertThat(within.perQuestion.first().byAnswerPct).isEqualTo(mapOf("yes" to 100))
        assertThat(within.filterApplied).containsEntry("outsidePurview", "false")

        val outside = resultsController.get(pollId, withinPurview = false, outsidePurview = true)
        assertThat(outside.perQuestion.first().byAnswerPct).isEqualTo(mapOf("no" to 100))
        assertThat(resultsController.get(pollId, principal = sup).totalRespondents).isEqualTo(6)
    }

    @Test
    fun `zipcode filter at or above threshold returns aggregates`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publishOneQuestionPoll(creator)

        // Exactly threshold (3) at 90001, plus noise elsewhere
        repeat(3) { submit(pollId, fixtures.createUser(zipcode = "90001", emailPrefix = "a$it"), "Yes") }
        repeat(3) { submit(pollId, fixtures.createUser(zipcode = "90012", emailPrefix = "b$it"), "No") }

        val filtered = resultsController.get(pollId, zipcodes = listOf("90001"))
        assertThat(filtered.suppressed).isFalse
        assertThat(filtered.perQuestion.first().byAnswerPct).isEqualTo(mapOf("yes" to 100))
    }

    @Test
    fun `results say where the poll is for`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val zipPoll = publishOneQuestionPoll(creator)  // purview: ZIP 90001
        resultsController.get(zipPoll).let {
            assertThat(it.purviewLevel).isEqualTo(org.kodewerks.pollsystem.model.ScopeLevel.ZIP)
            assertThat(it.purviewAreas).containsExactly("90001")
        }

        val ca = fixtures.stateId("CA")
        val statewide = service.saveDraft(creator, QuestionnaireDraftRequest(
            pollTypeId = pollTypeId, title = "Statewide", summary = "s", closeDate = null,
            questions = listOf(QuestionInput("Q?")),
            scopeLevel = org.kodewerks.pollsystem.model.ScopeLevel.STATE, regionIds = listOf(ca)
        ))
        service.publish(statewide.id, creator, confirmed = false)
        // Hidden results (no responses yet) still say where the poll is for.
        resultsController.get(statewide.id, zipcodes = listOf("90001")).let {
            assertThat(it.visibility.mode).isEqualTo("HIDDEN")
            assertThat(it.purviewLevel).isEqualTo(org.kodewerks.pollsystem.model.ScopeLevel.STATE)
            assertThat(it.purviewAreas).containsExactly("California")
        }
    }
}
