package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.security.AppUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class BallotMeasureResultsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var elections: ElectionService
    @Autowired private lateinit var measures: BallotMeasureService
    @Autowired private lateinit var responseController: BallotMeasureResponseController
    @Autowired private lateinit var resultsController: BallotMeasureResultsController
    @Autowired private lateinit var fixtures: TestFixtures

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
                title = "Bond measure",
                summary = "Approve $1M bond?",
                effectiveDate = futureDate
            )
        )
        measures.publish(draft.id, creator, confirmed = false)
        return draft.id
    }

    private fun vote(measureId: Long, voter: User, response: Boolean) {
        responseController.submit(
            AppUserDetails(voter),
            measureId,
            SubmitBallotResponseRequest(response = response)
        )
    }

    @Test
    fun `aggregates yes and no across respondents`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val measureId = publishMeasure(creator)

        repeat(4) { vote(measureId, fixtures.createUser(emailPrefix = "y$it"), true) }
        repeat(2) { vote(measureId, fixtures.createUser(emailPrefix = "n$it"), false) }

        val results = resultsController.get(measureId)
        assertThat(results.suppressed).isFalse
        assertThat(results.totalRespondents).isEqualTo(6)
        assertThat(results.yes).isEqualTo(4)
        assertThat(results.no).isEqualTo(2)
    }

    @Test
    fun `zipcode filter below k-anonymity threshold suppresses`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val measureId = publishMeasure(creator)

        // 2 voters at 90001 — under threshold (3)
        repeat(2) { vote(measureId, fixtures.createUser(zipcode = "90001", emailPrefix = "a$it"), true) }
        // 4 elsewhere
        repeat(4) { vote(measureId, fixtures.createUser(zipcode = "90012", emailPrefix = "b$it"), false) }

        val filtered = resultsController.get(measureId, zipcodes = listOf("90001"))
        assertThat(filtered.suppressed).isTrue
        assertThat(filtered.totalRespondents).isEqualTo(0)

        val unfiltered = resultsController.get(measureId)
        assertThat(unfiltered.suppressed).isFalse
        assertThat(unfiltered.totalRespondents).isEqualTo(6)
    }
}
