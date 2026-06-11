package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.CandidateRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class ElectionResultsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var elections: ElectionService
    @Autowired private lateinit var responseController: ElectionResponseController
    @Autowired private lateinit var resultsController: ElectionResultsController
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var candidates: CandidateRepository

    private val electionPollTypeId = 1L
    private val futureDate get() = LocalDate.now().plusDays(30)

    private fun publish(creator: User, names: List<String>): Long {
        val draft = elections.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = electionPollTypeId,
                title = "Election",
                date = futureDate,
                zipcode = "90001",
                candidates = names.map { CandidateInput(it, "Indep", "Mayor") }
            )
        )
        elections.publish(draft.id, creator, confirmed = false)
        return draft.id
    }

    private fun vote(pollId: Long, voter: User, votes: Map<String, Boolean>) {
        val cs = candidates.findByElectionId(pollId)
        responseController.submit(
            AppUserDetails(voter),
            pollId,
            SubmitElectionResponsesRequest(answers = cs.map {
                CandidateAnswerInput(it.id, response = votes.getValue(it.name))
            })
        )
    }

    @Test
    fun `aggregates yes and no per candidate`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publish(creator, listOf("Alice", "Bob"))

        // 3 vote Yes-Alice, No-Bob
        repeat(3) {
            vote(pollId, fixtures.createUser(emailPrefix = "v$it"), mapOf("Alice" to true, "Bob" to false))
        }
        // 1 votes No-Alice, Yes-Bob
        vote(pollId, fixtures.createUser(emailPrefix = "alt"), mapOf("Alice" to false, "Bob" to true))

        val results = resultsController.get(pollId)
        assertThat(results.suppressed).isFalse
        assertThat(results.totalRespondents).isEqualTo(4)

        val byName = results.perCandidate.associateBy { it.name }
        assertThat(byName["Alice"]?.yes).isEqualTo(3)
        assertThat(byName["Alice"]?.no).isEqualTo(1)
        assertThat(byName["Bob"]?.yes).isEqualTo(1)
        assertThat(byName["Bob"]?.no).isEqualTo(3)
    }

    @Test
    fun `zipcode filter below k-anonymity threshold suppresses`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publish(creator, listOf("Alice"))

        // 2 voters at 90001 — under threshold (3)
        repeat(2) {
            vote(pollId, fixtures.createUser(zipcode = "90001", emailPrefix = "a$it"), mapOf("Alice" to true))
        }
        // 4 elsewhere
        repeat(4) {
            vote(pollId, fixtures.createUser(zipcode = "90012", emailPrefix = "b$it"), mapOf("Alice" to false))
        }

        val filtered = resultsController.get(pollId, zipcodes = listOf("90001"))
        assertThat(filtered.suppressed).isTrue

        val unfiltered = resultsController.get(pollId)
        assertThat(unfiltered.suppressed).isFalse
        assertThat(unfiltered.totalRespondents).isEqualTo(6)
    }
}
