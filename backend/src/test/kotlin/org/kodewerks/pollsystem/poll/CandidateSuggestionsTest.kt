package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

/** The election form's "From your other elections" suggestions. */
class CandidateSuggestionsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var elections: ElectionService
    @Autowired private lateinit var controller: ElectionController
    @Autowired private lateinit var fixtures: TestFixtures

    private fun election(c: User, vararg cs: CandidateInput) = elections.saveDraft(c, ElectionDraftRequest(
        pollTypeId = 1L, title = "E", date = LocalDate.now().plusDays(30), scopeLevel = ScopeLevel.NATIONAL, candidates = cs.toList()
    ))

    @Test
    fun `own other elections only, each candidate once, by office then name`() {
        val me = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "cs-me")
        val current = election(me, CandidateInput("Current Only", "X", "Mayor"))
        election(me, CandidateInput("Judy James", "Blue", "District 1"), CandidateInput("Jim James", "Red", "District 1"))
        election(me, CandidateInput("jim james", "red", "district 1"), CandidateInput("Barney Rubble", "Red", "District 2"))
        val someoneElse = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "cs-other")
        election(someoneElse, CandidateInput("Not Mine", "Green", "District 1"))

        val got = controller.candidateSuggestions(AppUserDetails(me), excludeElectionId = current.id)
        assertThat(got.map { "${it.officeName}: ${it.name}" })
            .containsExactly("District 1: Jim James", "District 1: Judy James", "District 2: Barney Rubble")
    }
}
