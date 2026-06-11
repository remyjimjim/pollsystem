package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.BallotResponseRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

class BallotMeasureResponsesTest : AbstractIntegrationTest() {

    @Autowired private lateinit var elections: ElectionService
    @Autowired private lateinit var measures: BallotMeasureService
    @Autowired private lateinit var controller: BallotMeasureResponseController
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var responses: BallotResponseRepository

    private val electionPollTypeId = 1L
    private val ballotMeasurePollTypeId = 3L
    private val futureDate get() = LocalDate.now().plusDays(30)

    private fun publishMeasure(creator: User): Long {
        val parent = elections.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = electionPollTypeId,
                title = "Parent",
                date = futureDate,
                zipcode = "90001",
                candidates = listOf(CandidateInput("A", "X", "Mayor"))
            )
        )
        val draft = measures.saveDraft(
            creator,
            BallotMeasureDraftRequest(
                pollTypeId = ballotMeasurePollTypeId,
                electionId = parent.id,
                title = "Bond",
                summary = "Approve $1M bond?",
                effectiveDate = futureDate
            )
        )
        measures.publish(draft.id, creator, confirmed = false)
        return draft.id
    }

    @Test
    fun `first vote creates one BallotResponse`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val measureId = publishMeasure(creator)
        val voter = fixtures.createUser(emailPrefix = "voter")

        val result = controller.submit(
            AppUserDetails(voter),
            measureId,
            SubmitBallotResponseRequest(response = true, comment = "support")
        )

        assertThat(result.hasResponse).isTrue
        assertThat(result.response).isTrue
        assertThat(responses.findByMeasureId(measureId)).hasSize(1)
    }

    @Test
    fun `re-vote updates rather than duplicates`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val measureId = publishMeasure(creator)
        val voter = fixtures.createUser(emailPrefix = "voter")

        controller.submit(
            AppUserDetails(voter),
            measureId,
            SubmitBallotResponseRequest(response = true)
        )
        controller.submit(
            AppUserDetails(voter),
            measureId,
            SubmitBallotResponseRequest(response = false, comment = "changed")
        )

        val rows = responses.findByMeasureId(measureId).filter { it.user.id == voter.id }
        assertThat(rows).hasSize(1)
        assertThat(rows[0].response).isFalse
        assertThat(rows[0].comment).isEqualTo("changed")
        assertThat(rows[0].lastModified).isNotNull
    }

    @Test
    fun `submit on a DRAFT measure is rejected`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val parent = elections.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = electionPollTypeId,
                title = "Parent",
                date = futureDate,
                zipcode = "90001",
                candidates = listOf(CandidateInput("A", "X", "Mayor"))
            )
        )
        val draft = measures.saveDraft(
            creator,
            BallotMeasureDraftRequest(
                pollTypeId = ballotMeasurePollTypeId,
                electionId = parent.id,
                title = "Draft only",
                summary = "summary",
                effectiveDate = futureDate
            )
        )
        val voter = fixtures.createUser(emailPrefix = "voter")

        assertThatThrownBy {
            controller.submit(
                AppUserDetails(voter),
                draft.id,
                SubmitBallotResponseRequest(response = true)
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(409)
        }
    }
}
