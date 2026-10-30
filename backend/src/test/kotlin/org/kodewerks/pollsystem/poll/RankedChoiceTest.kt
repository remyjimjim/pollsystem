package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.CandidateRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

/** Ranked-choice races, from vote to round-by-round results (V44, decided 2026-10-30). */
class RankedChoiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: ElectionService
    @Autowired private lateinit var votes: ElectionResponseController
    @Autowired private lateinit var results: ElectionResultsController
    @Autowired private lateinit var candidates: CandidateRepository
    @Autowired private lateinit var rankedRaces: RankedRaces
    @Autowired private lateinit var fixtures: TestFixtures

    /** "Senate" ranked with Ann, Bob, Cal, Dee; "Mayor" single-choice with Eve and Fay. */
    private fun ballot(): Long {
        val c = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "rcv-creator")
        val tag = System.nanoTime()
        val id = service.saveDraft(c, ElectionDraftRequest(
            pollTypeId = 1L, title = "RCV $tag", date = LocalDate.now().plusDays(10), scopeLevel = ScopeLevel.NATIONAL,
            candidates = listOf("Ann", "Bob", "Cal", "Dee").map { CandidateInput(it, "Ind", "RCV Senate $tag") } +
                listOf("Eve", "Fay").map { CandidateInput(it, "Ind", "RCV Mayor $tag") }
        )).id
        service.publish(id, c, confirmed = true)
        rankedRaces.set(id, listOf(candidates.findByElectionId(id).first { it.name == "Ann" }.office.id))
        return id
    }

    private fun cand(id: Long, name: String) = candidates.findByElectionId(id).first { it.name == name }.id

    /** [ranks]: Senate names in order of preference; [mayor]: the single pick. */
    private fun vote(id: Long, u: User, ranks: List<String>, mayor: String = "Eve", raw: Map<String, Int?>? = null) {
        val answers = candidates.findByElectionId(id).map { c ->
            if (c.name in listOf("Eve", "Fay")) CandidateAnswerInput(c.id, c.name == mayor)
            else {
                val rank = raw?.get(c.name) ?: ranks.indexOf(c.name).takeIf { it >= 0 }?.plus(1)
                CandidateAnswerInput(c.id, rank != null, rank = rank)
            }
        }
        votes.submit(AppUserDetails(u), id, SubmitElectionResponsesRequest(answers))
    }

    private fun refused(message: String, block: () -> Unit) = assertThatThrownBy(block)
        .isInstanceOf(ResponseStatusException::class.java)
        .matches { (it as ResponseStatusException).statusCode.value() == 400 }
        .hasMessageContaining(message)

    @Test
    fun `the ballot says which races are ranked, and ranks are stored and read back`() {
        val id = ballot()
        assertThat(service.toDto(service.get(id)).rankedOfficeIds).containsExactly(candidates.findByElectionId(id).first { it.name == "Ann" }.office.id)
        val voter = fixtures.createUser(emailPrefix = "rcv-voter")
        vote(id, voter, listOf("Cal", "Ann"))
        val mine = votes.mine(AppUserDetails(voter), id).responses.associate { r -> candidates.findById(r.candidateId).get().name to r.rank }
        assertThat(mine).isEqualTo(mapOf("Ann" to 2, "Bob" to null, "Cal" to 1, "Dee" to null, "Eve" to null, "Fay" to null))
    }

    @Test
    fun `ranks must start at 1st and run without gaps or repeats, and only ranked candidates carry one`() {
        val id = ballot()
        val voter = fixtures.createUser(emailPrefix = "rcv-bad")
        refused("Rank at least your 1st choice") { vote(id, voter, emptyList()) }
        refused("no gaps or repeats") { vote(id, voter, emptyList(), raw = mapOf("Ann" to 1, "Bob" to 3)) }
        refused("no gaps or repeats") { vote(id, voter, emptyList(), raw = mapOf("Ann" to 1, "Bob" to 1)) }
        refused("no gaps or repeats") { vote(id, voter, emptyList(), raw = mapOf("Ann" to 2)) }
        val answers = candidates.findByElectionId(id).map { CandidateAnswerInput(it.id, it.name == "Ann" || it.name == "Eve", rank = if (it.name == "Ann") 1 else if (it.name == "Bob") 2 else null) }
        refused("Only ranked candidates") { votes.submit(AppUserDetails(voter), id, SubmitElectionResponsesRequest(answers)) }
    }

    @Test
    fun `results count the ranked race round by round, and leave the single-choice race as it was`() {
        val id = ballot()
        // Ann 4, Bob 3, Cal 2 (then Bob), Dee 1 (then Cal): Dee out, then Cal (fewer in round 1), Bob wins 5-4, 1 exhausted.
        repeat(4) { vote(id, fixtures.createUser(emailPrefix = "rcv-a$it"), listOf("Ann")) }
        repeat(3) { vote(id, fixtures.createUser(emailPrefix = "rcv-b$it"), listOf("Bob")) }
        repeat(2) { vote(id, fixtures.createUser(emailPrefix = "rcv-c$it"), listOf("Cal", "Bob")) }
        vote(id, fixtures.createUser(emailPrefix = "rcv-d"), listOf("Dee", "Cal"))

        val sup = AppUserDetails(fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "rcv-super"))
        val res = results.get(id, principal = sup)
        val race = res.rankedRaces.single()
        assertThat(race.ballots).isEqualTo(10)
        assertThat(race.rounds.map { it.eliminated }).containsExactly(cand(id, "Dee"), cand(id, "Cal"), null)
        val last = race.rounds.last()
        assertThat(last.winner).isEqualTo(cand(id, "Bob"))
        assertThat(last.votes).isEqualTo(mapOf(cand(id, "Ann") to 4, cand(id, "Bob") to 5))
        assertThat(last.shares).isEqualTo(mapOf(cand(id, "Ann") to 44, cand(id, "Bob") to 56))
        assertThat(last.exhaustedPct).isEqualTo(10)
        // The single-choice race is unchanged.
        assertThat(res.perCandidate.single { it.name == "Eve" }.yes).isEqualTo(10)

        // Public viewers: the same rounds, percentages only.
        val public = results.get(id).rankedRaces.single()
        assertThat(public.rounds.last().winner).isEqualTo(cand(id, "Bob"))
        assertThat(public.rounds.last().votes).isNull()
        assertThat(public.ballots).isNull()
    }
}
