package com.pollsystem.poll

import com.pollsystem.AbstractIntegrationTest
import com.pollsystem.TestFixtures
import com.pollsystem.model.AccessLevel
import com.pollsystem.model.PollStatus
import com.pollsystem.model.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class BallotMeasureServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var elections: ElectionService
    @Autowired private lateinit var measures: BallotMeasureService
    @Autowired private lateinit var fixtures: TestFixtures

    private val electionPollTypeId = 1L
    private val ballotMeasurePollTypeId = 3L  // V1 seed: id=3 = Referendum/Ballot Measure
    private val futureDate get() = LocalDate.now().plusDays(30)

    private fun seedElection(creator: User) =
        elections.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = electionPollTypeId,
                title = "Parent Election",
                date = futureDate,
                zipcode = "90001",
                candidates = listOf(CandidateInput("A", "X", "Mayor"))
            )
        )

    @Test
    fun `save draft attaches BM to creator's own election`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val parent = seedElection(creator)

        val saved = measures.saveDraft(
            creator,
            BallotMeasureDraftRequest(
                pollTypeId = ballotMeasurePollTypeId,
                electionId = parent.id,
                title = "Bond measure",
                summary = "Approve $1M bond?",
                effectiveDate = futureDate,
                closeDate = Instant.now().plus(10, ChronoUnit.DAYS)
            )
        )

        assertThat(saved.status).isEqualTo(PollStatus.DRAFT)
        assertThat(saved.election.id).isEqualTo(parent.id)
    }

    @Test
    fun `cannot attach BM to another creator's election`() {
        val creatorA = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "ca")
        val creatorB = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "cb")
        val parent = seedElection(creatorA)

        assertThatThrownBy {
            measures.saveDraft(
                creatorB,
                BallotMeasureDraftRequest(
                    pollTypeId = ballotMeasurePollTypeId,
                    electionId = parent.id,
                    title = "Hijacked",
                    summary = "Summary",
                    effectiveDate = futureDate
                )
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(403)
        }
    }

    @Test
    fun `publish with close_date less than 5 days ahead requires confirmation`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val parent = seedElection(creator)
        val draft = measures.saveDraft(
            creator,
            BallotMeasureDraftRequest(
                pollTypeId = ballotMeasurePollTypeId,
                electionId = parent.id,
                title = "Close soon",
                summary = "Summary",
                effectiveDate = futureDate,
                closeDate = Instant.now().plus(2, ChronoUnit.DAYS)
            )
        )

        assertThatThrownBy { measures.publish(draft.id, creator, confirmed = false) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(422)
                assertThat(it.reason).startsWith("close_date_short:")
            }

        val published = measures.publish(draft.id, creator, confirmed = true)
        assertThat(published.status).isEqualTo(PollStatus.PUBLISHED)
    }

    @Test
    fun `publish without close_date succeeds (no candidate-style requirement)`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val parent = seedElection(creator)
        val draft = measures.saveDraft(
            creator,
            BallotMeasureDraftRequest(
                pollTypeId = ballotMeasurePollTypeId,
                electionId = parent.id,
                title = "Open",
                summary = "Summary",
                effectiveDate = futureDate,
                closeDate = null
            )
        )

        val published = measures.publish(draft.id, creator, confirmed = false)
        assertThat(published.status).isEqualTo(PollStatus.PUBLISHED)
    }

    @Test
    fun `update is rejected once published`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val parent = seedElection(creator)
        val draft = measures.saveDraft(
            creator,
            BallotMeasureDraftRequest(
                pollTypeId = ballotMeasurePollTypeId,
                electionId = parent.id,
                title = "title",
                summary = "summary",
                effectiveDate = futureDate
            )
        )
        measures.publish(draft.id, creator, confirmed = false)

        assertThatThrownBy {
            measures.update(
                draft.id,
                creator,
                BallotMeasureDraftRequest(
                    pollTypeId = ballotMeasurePollTypeId,
                    electionId = parent.id,
                    title = "modified",
                    summary = "summary",
                    effectiveDate = futureDate
                )
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(409)
        }
    }
}
