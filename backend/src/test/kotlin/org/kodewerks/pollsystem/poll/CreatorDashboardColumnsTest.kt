package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.BlockScope
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollTypeBlock
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.PollTypeBlockRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The Creator dashboard's For Election and Enabled? columns. */
class CreatorDashboardColumnsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var questionnaireService: QuestionnaireService
    @Autowired private lateinit var electionService: ElectionService
    @Autowired private lateinit var ballotMeasureService: BallotMeasureService
    @Autowired private lateinit var creatorPolls: CreatorPollsController
    @Autowired private lateinit var blocks: PollTypeBlockRepository
    @Autowired private lateinit var fixtures: TestFixtures

    private fun creator() = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "dash-cols")
    private fun rows(c: User) = creatorPolls.list(AppUserDetails(c), showArchived = false)

    @Test
    fun `a ballot measure names its election, other types don't`() {
        val c = creator()
        val election = electionService.saveDraft(c, ElectionDraftRequest(
            pollTypeId = 1L, title = "November General", date = LocalDate.now().plusDays(30),
            scopeLevel = ScopeLevel.NATIONAL, candidates = listOf(CandidateInput("A", "Ind", "Mayor"))
        ))
        val measure = ballotMeasureService.saveDraft(c, BallotMeasureDraftRequest(
            pollTypeId = 3L, electionId = election.id, title = "Prop 1", summary = "Approve?",
            effectiveDate = LocalDate.now().plusDays(30), closeDate = Instant.now().plus(30, ChronoUnit.DAYS)
        ))

        val m = rows(c).single { it.type == "BallotMeasure" && it.id == measure.id }
        assertThat(m.electionId).isEqualTo(election.id)
        assertThat(m.electionTitle).isEqualTo("November General")
        val e = rows(c).single { it.type == "Election" && it.id == election.id }
        assertThat(e.electionTitle).isNull()
    }

    @Test
    fun `Enabled is YES until an admin switches the poll off, PARTLY in some areas, NO everywhere`() {
        val c = creator()
        val blocker = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "dash-admin")
        val id = questionnaireService.saveDraft(c, QuestionnaireDraftRequest(
            pollTypeId = 2L, title = "Park hours", summary = "s", closeDate = null,
            questions = listOf(QuestionInput("Q?")), zipcodes = listOf("90001")
        )).id
        fun enabled() = rows(c).single { it.type == "Questionnaire" && it.id == id }.enabled
        fun block(scope: BlockScope, zip: String? = null) =
            blocks.save(PollTypeBlock(pollType = PollKind.QUESTIONNAIRE, pollId = id, scope = scope, zipcode = zip, createdBy = blocker.id))

        assertThat(enabled()).isEqualTo("YES")
        block(BlockScope.ZIPCODE, zip = "90001")
        assertThat(enabled()).isEqualTo("PARTLY")
        block(BlockScope.EVERYWHERE)
        assertThat(enabled()).isEqualTo("NO")
    }
}
