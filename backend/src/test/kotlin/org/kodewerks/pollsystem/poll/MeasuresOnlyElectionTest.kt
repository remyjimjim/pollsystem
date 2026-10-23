package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.model.ScopeLevel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A state with statewide measures but no statewide races still needs an election for them. */
class MeasuresOnlyElectionTest : AbstractIntegrationTest() {

    @Autowired private lateinit var elections: ElectionService
    @Autowired private lateinit var measures: BallotMeasureService
    @Autowired private lateinit var fixtures: TestFixtures

    private fun draft(c: org.kodewerks.pollsystem.model.User) = elections.saveDraft(c, ElectionDraftRequest(
        pollTypeId = 1L, title = "Measures only", date = LocalDate.now().plusDays(10), scopeLevel = ScopeLevel.NATIONAL
    ))

    @Test
    fun `an election with ballot measures but no candidates can be published, and its measures go live`() {
        val c = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "mo")
        val e = draft(c)
        val m = measures.saveDraft(c, BallotMeasureDraftRequest(pollTypeId = 3L, electionId = e.id, title = "Prop 1", summary = "Approve?",
            effectiveDate = LocalDate.now().plusDays(40), closeDate = Instant.now().plus(10, ChronoUnit.DAYS)))
        elections.publish(e.id, c, confirmed = true)
        assertThat(elections.get(e.id).status).isEqualTo(PollStatus.PUBLISHED)
        assertThat(measures.get(m.id).status).isEqualTo(PollStatus.PUBLISHED)
    }

    @Test
    fun `a district ballot with no House race of its own publishes, since it includes the statewide election`() {
        val c = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "mo-inc")
        val statewide = elections.saveDraft(c, ElectionDraftRequest(pollTypeId = 1L, title = "Statewide", date = LocalDate.now().plusDays(10),
            scopeLevel = ScopeLevel.NATIONAL, candidates = listOf(CandidateInput("S", "Ind", "U.S. Senator"))))
        elections.publish(statewide.id, c, confirmed = true)
        val district = elections.saveDraft(c, ElectionDraftRequest(pollTypeId = 1L, title = "District 10 (unopposed)", date = LocalDate.now().plusDays(10),
            scopeLevel = ScopeLevel.NATIONAL, includedElectionIds = listOf(statewide.id)))
        elections.publish(district.id, c, confirmed = true)
        assertThat(elections.get(district.id).status).isEqualTo(PollStatus.PUBLISHED)
    }

    @Test
    fun `with neither candidates nor measures it is still refused`() {
        val c = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "mo-empty")
        val e = draft(c)
        assertThatThrownBy { elections.publish(e.id, c, confirmed = true) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("At least one candidate, ballot measure or included election required")
    }
}
