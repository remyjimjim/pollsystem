package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.CandidateRepository
import org.kodewerks.pollsystem.repository.CandidateResponseRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

class ElectionResponsesTest : AbstractIntegrationTest() {

    @Autowired private lateinit var elections: ElectionService
    @Autowired private lateinit var controller: ElectionResponseController
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var candidates: CandidateRepository
    @Autowired private lateinit var responses: CandidateResponseRepository

    private val electionPollTypeId = 1L
    private val futureDate get() = LocalDate.now().plusDays(30)

    private fun publishElection(creator: User, candidateNames: List<String>): Long {
        val draft = elections.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = electionPollTypeId,
                title = "City Council",
                date = futureDate,
                zipcode = "90001",
                candidates = candidateNames.map { CandidateInput(it, "Indep", "Mayor") }
            )
        )
        elections.publish(draft.id, creator, confirmed = false)
        return draft.id
    }

    @Test
    fun `first vote creates one CandidateResponse per candidate`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publishElection(creator, listOf("Alice", "Bob"))
        val voter = fixtures.createUser(emailPrefix = "voter")
        val cs = candidates.findByElectionId(pollId)

        controller.submit(
            AppUserDetails(voter),
            pollId,
            SubmitElectionResponsesRequest(answers = cs.map {
                CandidateAnswerInput(it.id, response = it.name == "Alice")
            })
        )

        val rows = responses.findByElectionIdAndUserId(pollId, voter.id)
        assertThat(rows).hasSize(2)
        val byCandidate = rows.associateBy { it.candidate.name }
        assertThat(byCandidate["Alice"]?.response).isTrue
        assertThat(byCandidate["Bob"]?.response).isFalse
    }

    @Test
    fun `re-vote updates rather than duplicates`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publishElection(creator, listOf("Alice"))
        val voter = fixtures.createUser(emailPrefix = "voter")
        val cs = candidates.findByElectionId(pollId)

        controller.submit(
            AppUserDetails(voter),
            pollId,
            SubmitElectionResponsesRequest(answers = listOf(CandidateAnswerInput(cs[0].id, true)))
        )
        controller.submit(
            AppUserDetails(voter),
            pollId,
            SubmitElectionResponsesRequest(answers = listOf(CandidateAnswerInput(cs[0].id, false, "changed mind")))
        )

        val rows = responses.findByElectionIdAndUserId(pollId, voter.id)
        assertThat(rows).hasSize(1)
        assertThat(rows[0].response).isFalse
        assertThat(rows[0].comment).isEqualTo("changed mind")
        assertThat(rows[0].lastModified).isNotNull
    }

    @Test
    fun `partial answer set is rejected`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val pollId = publishElection(creator, listOf("Alice", "Bob"))
        val voter = fixtures.createUser(emailPrefix = "voter")
        val cs = candidates.findByElectionId(pollId)

        assertThatThrownBy {
            controller.submit(
                AppUserDetails(voter),
                pollId,
                SubmitElectionResponsesRequest(answers = listOf(CandidateAnswerInput(cs[0].id, true)))
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(400)
            assertThat(it.reason).contains("Missing answers")
        }
    }
}
