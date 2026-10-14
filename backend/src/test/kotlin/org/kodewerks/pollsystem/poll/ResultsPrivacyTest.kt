package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.QuestionRepository
import org.kodewerks.pollsystem.repository.ResultSnapshotRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired

/**
 * Small-poll privacy (decided 2026-10-14, option C; test threshold k = 3):
 * public results are hidden until k respondents, then move only in steps of k,
 * as rounded percentages; a super sees live raw results at all times.
 */
class ResultsPrivacyTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: QuestionnaireService
    @Autowired private lateinit var responses: QuestionnaireResponseController
    @Autowired private lateinit var results: QuestionnaireResultsController
    @Autowired private lateinit var questions: QuestionRepository
    @Autowired private lateinit var snapshots: ResultSnapshotService
    @Autowired private lateinit var snapshotRows: ResultSnapshotRepository
    @Autowired private lateinit var fixtures: TestFixtures

    private fun poll(): Long {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "priv-creator")
        val d = service.saveDraft(creator, QuestionnaireDraftRequest(
            pollTypeId = 2L, title = "Privacy", summary = "s", closeDate = null,
            questions = listOf(QuestionInput("Q?")), zipcodes = listOf("90001")
        ))
        service.publish(d.id, creator, confirmed = false)
        return d.id
    }

    private fun vote(pollId: Long, voter: User, answer: String) {
        val q = questions.findByQuestionnaireId(pollId).single()
        responses.submit(AppUserDetails(voter), pollId, SubmitResponsesRequest(listOf(QuestionAnswerInput(q.id, answer))))
    }

    private fun voter(i: Int) = fixtures.createUser(emailPrefix = "priv-v$i")
    private fun publicPct(pollId: Long) = results.get(pollId).perQuestion.singleOrNull()?.byAnswerPct
    private val sup by lazy { AppUserDetails(fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "priv-super")) }

    @Test
    fun `hidden until k people have responded, while a super sees them live`() {
        val id = poll()
        vote(id, voter(1), "Yes")
        vote(id, voter(2), "No")

        val public = results.get(id)
        assertThat(public.visibility.mode).isEqualTo("HIDDEN")
        assertThat(public.visibility.respondentsSoFar).isEqualTo(2)
        assertThat(public.visibility.minimum).isEqualTo(3)
        assertThat(public.perQuestion).isEmpty()

        val raw = results.get(id, principal = sup)
        assertThat(raw.visibility.mode).isEqualTo("LIVE")
        assertThat(raw.perQuestion.single().byAnswer).isEqualTo(mapOf("yes" to 1, "no" to 1))
    }

    @Test
    fun `public results move only in steps of k, so one new vote is never visible`() {
        val id = poll()
        repeat(3) { vote(id, voter(it), "Yes") }
        assertThat(publicPct(id)).isEqualTo(mapOf("yes" to 100))

        vote(id, voter(10), "No")                       // 4th: no new step
        assertThat(publicPct(id)).isEqualTo(mapOf("yes" to 100))
        assertThat(results.get(id).visibility.respondentsSoFar).isEqualTo(4)
        assertThat(results.get(id, principal = sup).perQuestion.single().byAnswer).isEqualTo(mapOf("yes" to 3, "no" to 1))

        vote(id, voter(11), "No")
        vote(id, voter(12), "No")                       // 6th: the next step
        assertThat(publicPct(id)).isEqualTo(mapOf("yes" to 50, "no" to 50))
    }

    @Test
    fun `a changed answer shows only with the next step`() {
        val id = poll()
        val first = voter(1)
        vote(id, first, "Yes"); vote(id, voter(2), "Yes"); vote(id, voter(3), "Yes")
        vote(id, first, "No")                           // an edit: same 3 respondents
        assertThat(publicPct(id)).isEqualTo(mapOf("yes" to 100))

        repeat(3) { vote(id, voter(20 + it), "Yes") }   // next step (6)
        assertThat(publicPct(id)).isEqualTo(mapOf("yes" to 83, "no" to 17))
    }

    @Test
    fun `a poll with k or more respondents but no snapshot gets one on refresh (the startup backfill)`() {
        val id = poll()
        repeat(3) { vote(id, voter(it), "Yes") }
        snapshotRows.delete(snapshotRows.findByPollTypeAndPollId(PollKind.QUESTIONNAIRE, id)!!)
        assertThat(results.get(id).visibility.mode).isEqualTo("HIDDEN")

        snapshots.refresh(PollKind.QUESTIONNAIRE, id)
        assertThat(publicPct(id)).isEqualTo(mapOf("yes" to 100))
    }
}
