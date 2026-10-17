package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.adminpolls.AdminPollsController
import org.kodewerks.pollsystem.adminpolls.CreateBlockRequest
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.BlockScope
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** A poll's history and Reopen (decided 2026-10-17: 1a, 2b, 3a). */
class PollHistoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var questionnaires: QuestionnaireService
    @Autowired private lateinit var questionnaireRepo: QuestionnaireRepository
    @Autowired private lateinit var elections: ElectionService
    @Autowired private lateinit var measures: BallotMeasureService
    @Autowired private lateinit var measureRepo: BallotMeasureRepository
    @Autowired private lateinit var creatorPolls: CreatorPollsController
    @Autowired private lateinit var adminPolls: AdminPollsController
    @Autowired private lateinit var milestones: PollMilestoneService
    @Autowired private lateinit var historyApi: PollHistoryController
    @Autowired private lateinit var fixtures: TestFixtures

    private fun creator(prefix: String = "ph-creator") = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = prefix)

    private fun draft(title: String = "History poll", closeDate: Instant? = null) = QuestionnaireDraftRequest(
        pollTypeId = 2L, title = title, summary = "s", closeDate = closeDate,
        questions = listOf(QuestionInput("Q?")), zipcodes = listOf("90001")
    )

    private fun publishedQuestionnaire(c: User): Long {
        val d = questionnaires.saveDraft(c, draft())
        questionnaires.publish(d.id, c, confirmed = true)
        return d.id
    }

    private fun history(viewer: User, id: Long, type: String = "questionnaire") =
        historyApi.list(AppUserDetails(viewer), type, id)

    @Test
    fun `publish, reopen, edit and publish again, each recorded with what changed`() {
        val c = creator()
        val id = publishedQuestionnaire(c)
        creatorPolls.reopen(AppUserDetails(c), "questionnaire", id)
        assertThat(questionnaireRepo.findById(id).get().status).isEqualTo(PollStatus.DRAFT)

        val newClose = Instant.parse("2027-01-15T18:00:00Z")
        questionnaires.update(id, c, draft(title = "History poll v2", closeDate = newClose))
        questionnaires.publish(id, c, confirmed = true)

        val h = history(c, id)
        assertThat(h.map { it.event }).containsExactly("PUBLISHED", "REOPENED", "PUBLISHED")
        assertThat(h.map { it.by }).containsOnly("you")
        assertThat(h[0].details).contains("Title: \"History poll\" → \"History poll v2\"")
            .contains("Close date: none → 2027-01-15 18:00 UTC")
        assertThat(h[2].details).isNull()                    // the first publish has nothing to compare
    }

    @Test
    fun `only a published or closed poll of your own can be reopened`() {
        val c = creator()
        val d = questionnaires.saveDraft(c, draft())
        assertThatThrownBy { creatorPolls.reopen(AppUserDetails(c), "questionnaire", d.id) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) { assertThat(it.statusCode.value()).isEqualTo(409) }
        val id = publishedQuestionnaire(c)
        assertThatThrownBy { creatorPolls.reopen(AppUserDetails(creator("ph-other")), "questionnaire", id) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) { assertThat(it.statusCode.value()).isEqualTo(403) }
    }

    @Test
    fun `archive and restore are recorded`() {
        val c = creator()
        val id = publishedQuestionnaire(c)
        creatorPolls.delete(AppUserDetails(c), "questionnaire", id)
        creatorPolls.restore(AppUserDetails(c), "questionnaire", id)
        assertThat(history(c, id).map { it.event }).containsExactly("RESTORED", "ARCHIVED", "PUBLISHED")
    }

    @Test
    fun `an election's measures published with it get their own entry`() {
        val c = creator()
        val e = elections.saveDraft(c, ElectionDraftRequest(
            pollTypeId = 1L, title = "E", date = LocalDate.now().plusDays(30), scopeLevel = ScopeLevel.NATIONAL,
            candidates = listOf(CandidateInput("A", "X", "Mayor"))))
        val m = measures.saveDraft(c, BallotMeasureDraftRequest(
            pollTypeId = 3L, electionId = e.id, title = "M", summary = "s", effectiveDate = LocalDate.now().plusDays(30)))
        elections.publish(e.id, c, confirmed = false)
        assertThat(history(c, e.id, "election").map { it.event }).containsExactly("PUBLISHED")
        assertThat(history(c, m.id, "ballot-measure").map { it.event }).containsExactly("PUBLISHED")
    }

    @Test
    fun `closing is recorded once, automatically`() {
        val c = creator()
        val id = publishedQuestionnaire(c)
        val closedAt = Instant.now().minus(Duration.ofHours(1))
        questionnaireRepo.save(questionnaireRepo.findById(id).get().copy(closeDate = closedAt))
        milestones.sendClosingReports()
        milestones.sendClosingReports()
        val h = history(c, id)
        assertThat(h.map { it.event }).containsExactly("CLOSED", "PUBLISHED")
        assertThat(h[0].by).isEqualTo("automatic")
    }

    @Test
    fun `an admin switching it off and on is recorded, and who sees what`() {
        val c = creator()
        val id = publishedQuestionnaire(c)
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "ph-admin")
        fixtures.assignAdmin(admin)                          // CA, which covers zip 90001
        val block = adminPolls.createBlock("questionnaire", id, CreateBlockRequest(BlockScope.EVERYWHERE, null, null, null), AppUserDetails(admin))
        adminPolls.deleteBlock(block.id, AppUserDetails(admin))

        // The creator sees the admin by role only.
        val mine = history(c, id)
        assertThat(mine.map { it.event }).containsExactly("SWITCHED_ON", "SWITCHED_OFF", "PUBLISHED")
        assertThat(mine[1].details).isEqualTo("Everywhere")
        assertThat(mine[1].by).isEqualTo("ADMIN")
        assertThat(mine[1].byEmail).isNull()

        // The admin (poll in their area) and a super see it, with emails.
        assertThat(history(admin, id)[2].byEmail).isEqualTo(c.email)
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "ph-super")
        assertThat(history(sup, id)).hasSize(3)

        // Another creator sees nothing of it; a member isn't allowed at all.
        assertThat(history(creator("ph-other"), id)).isEmpty()
        assertThatThrownBy { history(fixtures.createUser(emailPrefix = "ph-user"), id) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) { assertThat(it.statusCode.value()).isEqualTo(403) }
    }
}
