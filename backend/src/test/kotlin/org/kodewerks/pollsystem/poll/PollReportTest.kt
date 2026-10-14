package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.QuestionRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate

/** Creator reports, phase 2 (test k-anonymity threshold: 3). */
class PollReportTest : AbstractIntegrationTest() {

    @Autowired private lateinit var reports: PollReportService
    @Autowired private lateinit var creatorPolls: CreatorPollsController
    @Autowired private lateinit var questionnaires: QuestionnaireService
    @Autowired private lateinit var responses: QuestionnaireResponseController
    @Autowired private lateinit var questions: QuestionRepository
    @Autowired private lateinit var fixtures: TestFixtures

    // ---------- the privacy rules on their own ----------

    @Test
    fun `the timeline merges days forward until each period has at least k new respondents`() {
        fun day(d: Int, n: Int) = List(n) { Instant.parse("2026-10-%02dT12:00:00Z".format(d)) }
        val buckets = reports.timeline(day(1, 1) + day(2, 1) + day(3, 2) + day(4, 3) + day(5, 1))
        assertThat(buckets.map { Triple(it.from.dayOfMonth, it.to.dayOfMonth, it.count) })
            .containsExactly(Triple(1, 3, 4), Triple(4, 5, 4)) // day 5's 1 joins the period before
        assertThat(buckets.map { it.runningTotal }).containsExactly(4, 8)
        assertThat(buckets.first().from).isEqualTo(LocalDate.of(2026, 10, 1))
    }

    @Test
    fun `small areas are withheld, plus one more when a single one could be worked out`() {
        assertThat(reports.suppress(mapOf("A" to 5, "B" to 2, "C" to 4)).associate { it.label to it.count })
            .isEqualTo(mapOf("A" to 5, "B" to null, "C" to null)) // B alone = total - A - C, so C goes too
        assertThat(reports.suppress(mapOf("A" to 5, "B" to 2, "C" to 1)).associate { it.label to it.count })
            .isEqualTo(mapOf("A" to 5, "B" to null, "C" to null))
        assertThat(reports.suppress(mapOf("A" to 5, "B" to 4)).associate { it.label to it.count })
            .isEqualTo(mapOf("A" to 5, "B" to 4))
        // Zero reveals no one, and isn't used as the extra cell.
        assertThat(reports.suppress(mapOf("A" to 5, "B" to 0)).associate { it.label to it.count })
            .isEqualTo(mapOf("A" to 5, "B" to 0))
    }

    @Test
    fun `a small outside group can't be worked out from the areas and the total`() {
        // 15 respondents: 12 in one area, 2 outside (< 3). Showing 12 would give 15 - 12 = 2.
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "rep-creator")
        val id = poll(creator)
        repeat(12) { answer(id, fixtures.createUser(zipcode = "90001", emailPrefix = "rep-in$it")) }
        repeat(2) { answer(id, fixtures.createUser(zipcode = "10001", emailPrefix = "rep-out$it")) }

        val r = creatorPolls.report(AppUserDetails(creator), "questionnaire", id)
        assertThat(r.respondents).isEqualTo(14)
        assertThat(r.inArea).isNull()
        assertThat(r.reach!!.outside).isNull()
        assertThat(r.reach!!.rows.single().count).isNull() // withheld too
    }

    // ---------- a real poll ----------

    private fun poll(creator: User): Long {
        val d = questionnaires.saveDraft(creator, QuestionnaireDraftRequest(
            pollTypeId = 2L, title = "Report me", summary = "s", closeDate = null,
            questions = listOf(QuestionInput("Q1?"), QuestionInput("Q2?")), zipcodes = listOf("90001")
        ))
        questionnaires.publish(d.id, creator, confirmed = false)
        return d.id
    }

    private fun answer(pollId: Long, voter: User, response: String = "Yes", comment: String? = null) {
        responses.submit(AppUserDetails(voter), pollId, SubmitResponsesRequest(
            answers = questions.findByQuestionnaireId(pollId).map { QuestionAnswerInput(it.id, response, comment) }
        ))
    }

    @Test
    fun `a report counts people once, breaks the area down, and counts changes and comments`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "rep-creator")
        val id = poll(creator)
        val voters = List(4) { fixtures.createUser(zipcode = "90001", emailPrefix = "rep-in$it") }
        voters.forEach { answer(id, it) }
        repeat(3) { answer(id, fixtures.createUser(zipcode = "10001", emailPrefix = "rep-out$it")) }
        answer(id, voters[0], "No")                       // changed their answers
        answer(id, voters[1], "Yes", comment = "Nice")    // changed + commented

        val r = creatorPolls.report(AppUserDetails(creator), "questionnaire", id)
        assertThat(r.respondents).isEqualTo(7)
        assertThat(r.inArea).isEqualTo(4)
        assertThat(r.timeline!!.last().runningTotal).isEqualTo(7)
        assertThat(r.reach!!.level).isEqualTo(ScopeLevel.ZIP)
        assertThat(r.reach!!.rows.associate { it.label to it.count }).isEqualTo(mapOf("90001" to 4))
        assertThat(r.reach!!.outside).isEqualTo(3)
        assertThat(r.reach!!.covered to r.reach!!.total).isEqualTo(1 to 1)
        assertThat(r.engagement).isEqualTo(Engagement(changedAnswers = 2, commented = 1))
    }

    @Test
    fun `below the threshold only the totals show`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "rep-creator")
        val id = poll(creator)
        repeat(2) { answer(id, fixtures.createUser(zipcode = "90001", emailPrefix = "rep-few$it")) }

        val r = creatorPolls.report(AppUserDetails(creator), "questionnaire", id)
        assertThat(r.respondents).isEqualTo(2)
        assertThat(listOf(r.timeline, r.reach, r.engagement)).containsOnlyNulls()
    }

    @Test
    fun `only the poll's creator, or a super, can see its report`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "rep-creator")
        val id = poll(creator)
        val other = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "rep-other")
        assertThatThrownBy { creatorPolls.report(AppUserDetails(other), "questionnaire", id) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) { assertThat(it.statusCode.value()).isEqualTo(403) }
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "rep-super")
        assertThat(creatorPolls.report(AppUserDetails(sup), "questionnaire", id).respondents).isZero()
    }
}
