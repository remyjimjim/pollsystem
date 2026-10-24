package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.CandidateRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate

/** Launch settings (2026-10-24): totals from the first vote (k = 1), but a narrowed group under 4 is withheld. */
@TestPropertySource(properties = ["app.results.k-anonymity-threshold=1", "app.results.group-minimum=4"])
class GroupMinimumTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: ElectionService
    @Autowired private lateinit var votes: ElectionResponseController
    @Autowired private lateinit var results: ElectionResultsController
    @Autowired private lateinit var candidates: CandidateRepository
    @Autowired private lateinit var fixtures: TestFixtures

    @Test
    fun `one vote shows in the totals, but filtering to its zipcode or area group shows nothing`() {
        val c = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "gm")
        val e = service.saveDraft(c, ElectionDraftRequest(
            pollTypeId = 1L, title = "Group minimum", date = LocalDate.now().plusDays(10), scopeLevel = ScopeLevel.STATE,
            regionIds = listOf(5L), candidates = listOf(CandidateInput("A", "Ind", "Governor"), CandidateInput("B", "Ind", "Governor"))
        )).id
        service.publish(e, c, confirmed = true)
        val voter = fixtures.createUser(emailPrefix = "gm-v")   // zipcode 90001, California
        val cs = candidates.findByElectionId(e)
        votes.submit(AppUserDetails(voter), e, SubmitElectionResponsesRequest(cs.mapIndexed { i, x -> CandidateAnswerInput(x.id, i == 0) }))

        val all = results.get(e)
        assertThat(all.visibility.mode).isEqualTo("STEPPED")
        assertThat(all.visibility.groupMinimum).isEqualTo(4)
        assertThat(all.perCandidate.first { it.name == "A" }.yesPct).isEqualTo(100)
        assertThat(all.purviewCounts.inside).isNull()                       // 1 inside: withheld

        assertThat(results.get(e, zipcodes = listOf("90001")).suppressed).isTrue()
        assertThat(results.get(e, outsidePurview = false).suppressed).isTrue()
    }
}
