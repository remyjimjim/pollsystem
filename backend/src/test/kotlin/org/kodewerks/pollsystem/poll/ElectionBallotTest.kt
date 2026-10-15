package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * An election and its ballot measures as one ballot (decided 2026-10-15):
 * measures under a draft election go live with it (4a); a measure added to a
 * published election is published on its own (5a); the voting page lists only
 * published, still-open measures (3a); search says which election a measure is for.
 */
class ElectionBallotTest : AbstractIntegrationTest() {

    @Autowired private lateinit var elections: ElectionService
    @Autowired private lateinit var measures: BallotMeasureService
    @Autowired private lateinit var measureRepo: BallotMeasureRepository
    @Autowired private lateinit var electionRepo: ElectionRepository
    @Autowired private lateinit var measureController: BallotMeasureController
    @Autowired private lateinit var search: PollSearchController
    @Autowired private lateinit var fixtures: TestFixtures

    private fun creator() = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "eb-creator")

    private fun election(c: User, title: String = "King for a week") = elections.saveDraft(c, ElectionDraftRequest(
        pollTypeId = 1L, title = title, date = LocalDate.now().plusDays(30), scopeLevel = ScopeLevel.NATIONAL,
        candidates = listOf(CandidateInput("A", "X", "King"))
    ))

    private fun measure(c: User, electionId: Long, title: String, closeDate: Instant? = null) = measures.saveDraft(c, BallotMeasureDraftRequest(
        pollTypeId = 3L, electionId = electionId, title = title, summary = "s",
        effectiveDate = LocalDate.now().plusDays(30), closeDate = closeDate
    ))

    private fun status(measureId: Long) = measureRepo.findById(measureId).get().status

    @Test
    fun `a measure under a draft election can't be published on its own`() {
        val c = creator()
        val e = election(c)
        val m = measure(c, e.id, "Free Money Mondays")
        assertThatThrownBy { measures.publish(m.id, c, confirmed = true) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(409)
                assertThat(it.reason).contains("King for a week")
            }
        assertThat(status(m.id)).isEqualTo(PollStatus.DRAFT)
    }

    @Test
    fun `publishing the election publishes its draft measures with it`() {
        val c = creator()
        val e = election(c)
        val m1 = measure(c, e.id, "Free Money Mondays")
        val m2 = measure(c, e.id, "Nap Time Act")
        elections.publish(e.id, c, confirmed = false)
        assertThat(listOf(status(m1.id), status(m2.id))).containsOnly(PollStatus.PUBLISHED)
    }

    @Test
    fun `a measure that can't go live stops the whole publish`() {
        val c = creator()
        val e = election(c)
        val good = measure(c, e.id, "Fine one")
        val bad = measure(c, e.id, "Already over", closeDate = Instant.now().minus(1, ChronoUnit.DAYS))
        assertThatThrownBy { elections.publish(e.id, c, confirmed = true) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(400)
                assertThat(it.reason).contains("Already over")
            }
        assertThat(electionRepo.findById(e.id).get().status).isEqualTo(PollStatus.DRAFT)
        assertThat(listOf(status(good.id), status(bad.id))).containsOnly(PollStatus.DRAFT)
    }

    @Test
    fun `a measure closing soon needs the same confirmation when its election is published`() {
        val c = creator()
        val e = election(c)
        val m = measure(c, e.id, "Soon", closeDate = Instant.now().plus(2, ChronoUnit.DAYS))
        assertThatThrownBy { elections.publish(e.id, c, confirmed = false) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(422)
                assertThat(it.reason).startsWith("close_date_short:")
            }
        elections.publish(e.id, c, confirmed = true)
        assertThat(status(m.id)).isEqualTo(PollStatus.PUBLISHED)
    }

    @Test
    fun `a measure added after the election is published goes live on its own`() {
        val c = creator()
        val e = election(c)
        elections.publish(e.id, c, confirmed = false)
        val m = measure(c, e.id, "Late addition")
        measures.publish(m.id, c, confirmed = false)
        assertThat(status(m.id)).isEqualTo(PollStatus.PUBLISHED)
    }

    @Test
    fun `the creator sees every measure, voters only published open ones`() {
        val c = creator()
        val e = election(c)
        val open = measure(c, e.id, "Open")
        val closing = measure(c, e.id, "Closes later", closeDate = Instant.now().plus(10, ChronoUnit.DAYS))
        elections.publish(e.id, c, confirmed = false)
        val draft = measure(c, e.id, "Still a draft")
        // Closed since publishing:
        measureRepo.save(measureRepo.findById(closing.id).get().copy(closeDate = Instant.now().minus(1, ChronoUnit.HOURS)))

        assertThat(measureController.forElection(AppUserDetails(c), e.id).map { it.id })
            .containsExactly(open.id, closing.id, draft.id)
        val voter = fixtures.createUser(emailPrefix = "eb-voter")
        assertThat(measureController.forElection(AppUserDetails(voter), e.id).map { it.id }).containsExactly(open.id)
    }

    @Test
    fun `search says which election a measure is for`() {
        val c = creator()
        val e = election(c, "King for a week ${System.nanoTime()}")
        val m = measure(c, e.id, "Free Money Mondays ${System.nanoTime()}")
        elections.publish(e.id, c, confirmed = false)
        val rows = search.search(null, null, null, null, null, null, null, false)
        val mRow = rows.single { it.id == m.id && it.type == "BallotMeasure" }
        assertThat(mRow.electionId).isEqualTo(e.id)
        assertThat(mRow.electionTitle).isEqualTo(e.title)
        assertThat(rows.single { it.id == e.id && it.type == "Election" }.electionTitle).isNull()
    }
}
