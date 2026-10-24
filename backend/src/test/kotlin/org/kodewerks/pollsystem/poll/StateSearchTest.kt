package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.ScopeLevel
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

/** Searching one state finds that state's polls, not its neighbours' (some zipcodes cross state lines). */
class StateSearchTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: ElectionService
    @Autowired private lateinit var search: PollSearchController
    @Autowired private lateinit var fixtures: TestFixtures

    private val washington = 48L; private val idaho = 13L; private val oregon = 38L

    private fun statePoll(title: String, stateId: Long?): Long {
        val c = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "ss")
        val d = service.saveDraft(c, ElectionDraftRequest(
            pollTypeId = 1L, title = title, date = LocalDate.now().plusDays(10),
            scopeLevel = if (stateId == null) ScopeLevel.NATIONAL else ScopeLevel.STATE,
            regionIds = listOfNotNull(stateId), candidates = listOf(CandidateInput("A", "Ind", "Governor"))
        ))
        service.publish(d.id, c, confirmed = true)
        return d.id
    }

    @Test
    fun `a Washington search leaves out Idaho and Oregon polls`() {
        val wa = statePoll("StateSearch WA", washington)
        statePoll("StateSearch ID", idaho)
        statePoll("StateSearch OR", oregon)

        val found = search.search("StateSearch", null, null, listOf(washington), null, null, "Election", false).map { it.id }

        assertThat(found).containsExactly(wa)
    }

    @Test
    fun `Exclude nationwide leaves out polls for the whole country`() {
        val wa = statePoll("NationwideCheck WA", washington)
        val usa = statePoll("NationwideCheck USA", null)

        fun found(exclude: Boolean) =
            search.search("NationwideCheck", null, null, listOf(washington), null, null, "Election", false, exclude).map { it.id }

        assertThat(found(false)).containsExactlyInAnyOrder(wa, usa)
        assertThat(found(true)).containsExactly(wa)
        // Without a place picked too.
        assertThat(search.search("NationwideCheck", null, null, null, null, null, "Election", false, true).map { it.id }).containsExactly(wa)
    }
}
