package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

/** One ballot per district (V37): a district election includes the statewide one. */
class ElectionIncludesTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: ElectionService
    @Autowired private lateinit var search: PollSearchController
    @Autowired private lateinit var fixtures: TestFixtures

    private fun creator(prefix: String = "inc") = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = prefix)

    private fun request(title: String, candidate: String, office: String, includes: List<Long>? = null) = ElectionDraftRequest(
        pollTypeId = 1L, title = title, date = LocalDate.now().plusDays(10), scopeLevel = ScopeLevel.NATIONAL,
        candidates = listOf(CandidateInput(candidate, "Ind", office)), includedElectionIds = includes
    )

    private fun published(c: User, title: String, candidate: String, office: String, includes: List<Long>? = null): Long {
        val d = service.saveDraft(c, request(title, candidate, office, includes))
        service.publish(d.id, c, confirmed = true)
        return d.id
    }

    private fun electionsFound(title: String? = null, candidate: String? = null) =
        search.search(title, null, null, null, null, candidate, "Election", false).map { it.id }

    @Test
    fun `a district ballot includes the statewide election, which then drops out of search`() {
        val c = creator()
        val statewide = published(c, "Zedland statewide 2026", "Sam Senator", "U.S. Senator")
        assertThat(electionsFound(title = "Zedland")).containsExactly(statewide)

        val district = published(c, "Zedland District 1", "Hal House", "U.S. Representative, District 1", includes = listOf(statewide))
        assertThat(service.toDto(service.get(district)).includedElectionIds).containsExactly(statewide)
        assertThat(service.toDto(service.get(statewide)).includedByElectionIds).containsExactly(district)

        // Only the district ballot is listed; searching a statewide candidate finds it.
        assertThat(electionsFound(title = "Zedland")).containsExactly(district)
        assertThat(electionsFound(candidate = "Sam Senator")).containsExactly(district)
    }

    @Test
    fun `while the ballot is a draft, the statewide election is still listed`() {
        val c = creator()
        val statewide = published(c, "Yonder statewide 2026", "Sue Senator", "U.S. Senator")
        service.saveDraft(c, request("Yonder District 1", "Hank House", "U.S. Representative, District 1", listOf(statewide)))
        assertThat(electionsFound(title = "Yonder statewide")).containsExactly(statewide)
    }

    @Test
    fun `editing without includedElectionIds keeps them, and an empty list clears them`() {
        val c = creator()
        val statewide = published(c, "Xeno statewide", "A", "U.S. Senator")
        val d = service.saveDraft(c, request("Xeno District 1", "B", "U.S. Representative, District 1", listOf(statewide)))
        service.update(d.id, c, request("Xeno District 1, renamed", "B", "U.S. Representative, District 1", null))
        assertThat(service.toDto(service.get(d.id)).includedElectionIds).containsExactly(statewide)
        service.update(d.id, c, request("Xeno District 1", "B", "U.S. Representative, District 1", emptyList()))
        assertThat(service.toDto(service.get(d.id)).includedElectionIds).isEmpty()
    }

    @Test
    fun `refused - itself, another creator's election, or more than one level`() {
        val c = creator()
        val other = published(creator("inc-other"), "Theirs", "T", "U.S. Senator")
        val statewide = published(c, "Mine statewide", "S", "U.S. Senator")
        val district = published(c, "Mine District 1", "D", "U.S. Representative, District 1", listOf(statewide))
        val draft = service.saveDraft(c, request("Mine District 2", "E", "U.S. Representative, District 2"))

        fun refused(includes: List<Long>, id: Long = draft.id) = assertThatThrownBy {
            service.update(id, c, request("Mine District 2", "E", "U.S. Representative, District 2", includes))
        }.isInstanceOf(ResponseStatusException::class.java)

        refused(listOf(draft.id))                 // itself
        refused(listOf(other))                    // not mine (and I'm not a super)
        refused(listOf(district))                 // a ballot that includes others can't be included
        // A super may include anyone's election.
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "inc-super")
        val supDraft = service.saveDraft(sup, request("Super ballot", "F", "U.S. Representative, District 3", listOf(other)))
        assertThat(service.toDto(service.get(supDraft.id)).includedElectionIds).containsExactly(other)
    }
}
