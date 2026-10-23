package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.CandidateRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Midterm voting rules (V38): residents only, one district ballot per voter, and cancelling a vote. Test k = 3. */
class BallotRulesTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: ElectionService
    @Autowired private lateinit var measures: BallotMeasureService
    @Autowired private lateinit var votes: ElectionResponseController
    @Autowired private lateinit var measureVotes: BallotMeasureResponseController
    @Autowired private lateinit var candidates: CandidateRepository
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val california = 5L   // 90001 (the fixtures' default zipcode) is in California

    private fun election(c: User, title: String, office: String, includes: List<Long>? = null) = service.saveDraft(c, ElectionDraftRequest(
        pollTypeId = 1L, title = title, date = LocalDate.now().plusDays(10), scopeLevel = ScopeLevel.STATE,
        regionIds = listOf(california), candidates = listOf(CandidateInput("$title A", "Ind", office), CandidateInput("$title B", "Ind", office)),
        includedElectionIds = includes, residentsOnly = true
    )).id.also { service.publish(it, c, confirmed = true) }

    private fun vote(u: User, electionId: Long) {
        val cs = candidates.findByElectionId(electionId)
        votes.submit(AppUserDetails(u), electionId, SubmitElectionResponsesRequest(cs.mapIndexed { i, c -> CandidateAnswerInput(c.id, i == 0) }))
    }

    private fun refused(status: Int, message: String, block: () -> Unit) = assertThatThrownBy(block)
        .isInstanceOf(ResponseStatusException::class.java)
        .matches { (it as ResponseStatusException).statusCode.value() == status }
        .hasMessageContaining(message)

    @Test
    fun `only residents of the state may vote on its midterm ballot and its measures`() {
        val c = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "br-res")
        val statewide = service.saveDraft(c, ElectionDraftRequest(
            pollTypeId = 1L, title = "Res statewide", date = LocalDate.now().plusDays(10), scopeLevel = ScopeLevel.STATE,
            regionIds = listOf(california), candidates = listOf(CandidateInput("S", "Ind", "U.S. Senator")), residentsOnly = true
        )).id
        val m = measures.saveDraft(c, BallotMeasureDraftRequest(pollTypeId = 3L, electionId = statewide, title = "Res Prop 1", summary = "Approve?",
            effectiveDate = LocalDate.now().plusDays(40), closeDate = Instant.now().plus(10, ChronoUnit.DAYS)))
        service.publish(statewide, c, confirmed = true)

        val newYorker = fixtures.createUser(zipcode = "10001", emailPrefix = "br-ny")
        refused(403, "You can only vote on midterms from your own state.") { vote(newYorker, statewide) }
        refused(403, "You can only vote on midterms from your own state.") {
            measureVotes.submit(AppUserDetails(newYorker), m.id, SubmitBallotResponseRequest(response = true))
        }
        vote(fixtures.createUser(emailPrefix = "br-ca"), statewide)   // a Californian is fine
    }

    @Test
    fun `having voted on one district ballot, another in the state is refused until that vote is cancelled`() {
        val c = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "br-dist")
        val statewide = election(c, "Dist statewide", "U.S. Senator")
        val d1 = election(c, "Dist District 1", "U.S. Representative, District 1", listOf(statewide))
        val d2 = election(c, "Dist District 2", "U.S. Representative, District 2", listOf(statewide))
        val voter = fixtures.createUser(emailPrefix = "br-voter")
        vote(voter, d1); vote(voter, statewide)

        vote(voter, d1)   // changing your vote on the same ballot is fine
        refused(409, "You need to \"Cancel\" your vote from the other midterm you submitted (\"Dist District 1\")") { vote(voter, d2) }

        votes.cancel(AppUserDetails(voter), d1)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidate_responses WHERE user_id = ?", Int::class.java, voter.id)).isZero()
        vote(voter, d2); vote(voter, statewide)
    }

    @Test
    fun `another district ballot names the one you voted on, and cancelling there keeps your shared statewide votes`() {
        val c = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "br-keep")
        val statewide = election(c, "Keep statewide", "U.S. Senator")
        val d10 = election(c, "Keep District 10", "U.S. Representative, District 10", listOf(statewide))
        val d12 = election(c, "Keep District 12", "U.S. Representative, District 12", listOf(statewide))
        val voter = fixtures.createUser(emailPrefix = "br-keep-v")
        vote(voter, d10); vote(voter, statewide)

        assertThat(votes.mine(AppUserDetails(voter), d12).otherBallot).isEqualTo(OtherBallot(d10, "Keep District 10"))
        assertThat(votes.mine(AppUserDetails(voter), d10).otherBallot).isNull()

        votes.cancel(AppUserDetails(voter), d12)
        assertThat(votes.mine(AppUserDetails(voter), d10).responses).hasSize(2)
        assertThat(votes.mine(AppUserDetails(voter), statewide).responses).hasSize(2)   // still there
    }

    @Test
    fun `cancelling removes only that voter's rows from the published results`() {
        val c = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "br-snap")
        val e = election(c, "Snap statewide", "U.S. Senator")
        val voters = (1..4).map { fixtures.createUser(emailPrefix = "br-snap$it") }
        voters.take(3).forEach { vote(it, e) }   // 3 = k: results are published
        vote(voters[3], e)                        // the 4th isn't in them yet
        fun snap() = jdbc.queryForMap("SELECT id, respondents FROM result_snapshots WHERE poll_type = ? AND poll_id = ?", PollKind.ELECTION.name, e)
        val before = snap()
        assertThat(before["respondents"]).isEqualTo(3)

        votes.cancel(AppUserDetails(voters[0]), e)

        assertThat(snap()["respondents"]).isEqualTo(2)
        val users = jdbc.queryForList("SELECT DISTINCT user_id FROM result_snapshot_answers WHERE snapshot_id = ?", Long::class.java, before["id"])
        assertThat(users).containsExactlyInAnyOrder(voters[1].id, voters[2].id)   // the 4th voter is not revealed
    }
}
