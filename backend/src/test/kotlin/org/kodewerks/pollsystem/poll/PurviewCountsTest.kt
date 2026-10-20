package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.repository.QuestionRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired

/**
 * The results page's inside / outside counts (next to the purview checkboxes):
 * from the published snapshot, withheld for small groups, live for a super.
 * Test k = 3; the poll's purview is zip 90001.
 */
class PurviewCountsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: QuestionnaireService
    @Autowired private lateinit var questions: QuestionRepository
    @Autowired private lateinit var responses: QuestionnaireResponseController
    @Autowired private lateinit var results: QuestionnaireResultsController
    @Autowired private lateinit var fixtures: TestFixtures

    private fun poll(): Long {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "pc-creator")
        val d = service.saveDraft(creator, QuestionnaireDraftRequest(
            pollTypeId = 2L, title = "Counts", summary = "s", closeDate = null,
            questions = listOf(QuestionInput("Q?")), zipcodes = listOf("90001")))
        service.publish(d.id, creator, confirmed = true)
        return d.id
    }

    private fun vote(pollId: Long, zipcode: String) {
        val q = questions.findByQuestionnaireId(pollId).single()
        responses.submit(AppUserDetails(fixtures.createUser(emailPrefix = "pc-v", zipcode = zipcode)), pollId,
            SubmitResponsesRequest(listOf(QuestionAnswerInput(q.id, "Yes"))))
    }

    private fun counts(pollId: Long, asSuper: Boolean = false) = results.get(
        pollId,
        principal = if (asSuper) AppUserDetails(fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "pc-super")) else null
    ).purviewCounts.let { it.inside to it.outside }

    @Test
    fun `no counts while results are hidden, then the published step's counts`() {
        val id = poll()
        repeat(2) { vote(id, "90001") }
        assertThat(counts(id)).isEqualTo(null to null)                // under k: hidden

        vote(id, "90001")                                             // 3 inside: published
        assertThat(counts(id)).isEqualTo(3 to 0)

        vote(id, "80202")                                             // not in the snapshot yet…
        assertThat(counts(id)).isEqualTo(3 to 0)
        assertThat(counts(id, asSuper = true)).isEqualTo(3 to 1)      // …a super sees it live

        repeat(2) { vote(id, "80202") }                               // 6: the next step
        assertThat(counts(id)).isEqualTo(3 to 3)
    }

    @Test
    fun `a small group withholds both counts`() {
        val id = poll()
        repeat(5) { vote(id, "90001") }
        vote(id, "80202")                                             // 6 published: 5 inside, 1 outside
        assertThat(counts(id)).isEqualTo(null to null)
        assertThat(counts(id, asSuper = true)).isEqualTo(5 to 1)
    }
}
