package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.repository.CandidateRepository
import org.kodewerks.pollsystem.repository.OfficeRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

class ElectionServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: ElectionService
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var candidates: CandidateRepository
    @Autowired private lateinit var offices: OfficeRepository

    private val electionPollTypeId = 1L  // V1 seed: id=1 is Election
    private val futureDate get() = LocalDate.now().plusDays(30)

    @Test
    fun `save draft persists candidates and auto-creates an Office shared by candidates with same office name`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")

        val saved = service.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = electionPollTypeId,
                title = "City Council",
                date = futureDate,
                zipcode = "90001",
                candidates = listOf(
                    CandidateInput("Alice", "Indep", "Mayor"),
                    CandidateInput("Bob", "Indep", "Mayor")
                )
            )
        )

        assertThat(saved.status).isEqualTo(PollStatus.DRAFT)
        val list = candidates.findByElectionId(saved.id)
        assertThat(list).hasSize(2)
        // both candidates share the same Office row
        assertThat(list.map { it.office.id }.toSet()).hasSize(1)
    }

    @Test
    fun `Office upsert is case-insensitive`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")

        service.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = electionPollTypeId,
                title = "First",
                date = futureDate,
                zipcode = "90001",
                candidates = listOf(CandidateInput("A", "X", "MAYOR"))
            )
        )
        val countAfterFirst = offices.count()

        service.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = electionPollTypeId,
                title = "Second",
                date = futureDate,
                zipcode = "90001",
                candidates = listOf(CandidateInput("B", "Y", "mayor"))  // different case
            )
        )
        val countAfterSecond = offices.count()

        assertThat(countAfterSecond).isEqualTo(countAfterFirst)
    }

    @Test
    fun `update replaces all candidates atomically`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val draft = service.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = electionPollTypeId,
                title = "v1",
                date = futureDate,
                zipcode = "90001",
                candidates = listOf(CandidateInput("A", "X", "Mayor"))
            )
        )

        service.update(
            draft.id,
            creator,
            ElectionDraftRequest(
                pollTypeId = electionPollTypeId,
                title = "v2",
                date = futureDate,
                zipcode = "90001",
                candidates = listOf(
                    CandidateInput("B", "Y", "Mayor"),
                    CandidateInput("C", "Z", "Mayor")
                )
            )
        )

        val updated = candidates.findByElectionId(draft.id)
        assertThat(updated.map { it.name }).containsExactlyInAnyOrder("B", "C")
    }

    @Test
    fun `publish requires at least one candidate`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val draft = service.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = electionPollTypeId,
                title = "Empty",
                date = futureDate,
                zipcode = "90001",
                candidates = emptyList()
            )
        )
        assertThatThrownBy { service.publish(draft.id, creator, confirmed = false) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(400)
            }
    }

    @Test
    fun `update by a different creator is forbidden`() {
        val creatorA = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "ca")
        val creatorB = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "cb")
        val draft = service.saveDraft(
            creatorA,
            ElectionDraftRequest(
                pollTypeId = electionPollTypeId,
                title = "Mine",
                date = futureDate,
                zipcode = "90001",
                candidates = listOf(CandidateInput("A", "X", "Mayor"))
            )
        )

        assertThatThrownBy {
            service.update(
                draft.id,
                creatorB,
                ElectionDraftRequest(
                    pollTypeId = electionPollTypeId,
                    title = "Hijack",
                    date = futureDate,
                    zipcode = "90001",
                    candidates = listOf(CandidateInput("A", "X", "Mayor"))
                )
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(403)
        }
    }

    @Test
    fun `update is rejected once published`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val draft = service.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = electionPollTypeId,
                title = "title",
                date = futureDate,
                zipcode = "90001",
                candidates = listOf(CandidateInput("A", "X", "Mayor"))
            )
        )
        service.publish(draft.id, creator, confirmed = false)

        assertThatThrownBy {
            service.update(
                draft.id,
                creator,
                ElectionDraftRequest(
                    pollTypeId = electionPollTypeId,
                    title = "modified",
                    date = futureDate,
                    zipcode = "90001",
                    candidates = listOf(CandidateInput("A", "X", "Mayor"))
                )
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(409)
        }
    }
}
