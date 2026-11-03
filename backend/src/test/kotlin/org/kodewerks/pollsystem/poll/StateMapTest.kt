package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.CandidateRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

/** A state's district map (/maps/WA, decided 2026-11-03). Test threshold k = 3. */
class StateMapTest : AbstractIntegrationTest() {

    @Autowired private lateinit var elections: ElectionService
    @Autowired private lateinit var measures: BallotMeasureService
    @Autowired private lateinit var votes: ElectionResponseController
    @Autowired private lateinit var measureVotes: BallotMeasureResponseController
    @Autowired private lateinit var candidates: CandidateRepository
    @Autowired private lateinit var map: StateMapController
    @Autowired private lateinit var fixtures: TestFixtures

    @Test
    fun `parties come from the affiliation, the first one named for a fusion label`() {
        assertThat(partyOf("Prefers Democratic Party")).isEqualTo(Party.DEM)
        assertThat(partyOf("Registered Republican")).isEqualTo(Party.REP)
        for (ind in listOf("Independent", "No party listed", "Non-partisan", "Nonpartisan office"))
            assertThat(partyOf(ind)).describedAs(ind).isEqualTo(Party.IND)
        assertThat(partyOf("Democratic and Working Families")).isEqualTo(Party.DEM)
        assertThat(partyOf("Republican, Conservative")).isEqualTo(Party.REP)
        assertThat(partyOf("Libertarian")).isEqualTo(Party.OTHER)
    }

    private fun election(c: User, title: String, cands: List<Pair<String, String>>, office: String): Long {
        val id = elections.saveDraft(c, ElectionDraftRequest(
            pollTypeId = 1L, title = title, date = LocalDate.now().plusDays(10), scopeLevel = ScopeLevel.NATIONAL,
            candidates = cands.map { (n, a) -> CandidateInput(n, a, office) }
        )).id
        elections.publish(id, c, confirmed = true)
        return id
    }

    private fun vote(id: Long, u: User, pick: String) = votes.submit(AppUserDetails(u), id, SubmitElectionResponsesRequest(
        candidates.findByElectionId(id).map { CandidateAnswerInput(it.id, it.name == pick) }))

    @Test
    fun `each district shows its House race by party, and a measure split by the district's voters, with the results' privacy`() {
        val c = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "map-creator")
        val statewide = election(c, "2026 Midterm · North Dakota: statewide", listOf("Gov A" to "Independent"), "Map Governor")
        val m = measures.saveDraft(c, BallotMeasureDraftRequest(3L, statewide, "ND Measure 1", "s", LocalDate.now().plusDays(30))).id
        measures.publish(m, c, confirmed = true)
        val d1 = election(c, "2026 Midterm North Dakota District 1",
            listOf("Ann" to "Prefers Democratic Party", "Bob" to "Prefers Republican Party", "Cy" to "Independent"), "U.S. Representative, Map District 1")
        val d2 = election(c, "2026 Midterm North Dakota District 2",
            listOf("Dee" to "Registered Democrat", "Eli" to "Non-partisan"), "U.S. Representative, Map District 2")

        // District 1: Ann 3, Bob 1; all four answer the measure, Ann's voters Yes. District 2: 2 voters, Yes.
        repeat(3) { val u = fixtures.createUser(emailPrefix = "map-ann$it"); vote(d1, u, "Ann"); measureVotes.submit(AppUserDetails(u), m, SubmitBallotResponseRequest(true)) }
        fixtures.createUser(emailPrefix = "map-bob").let { vote(d1, it, "Bob"); measureVotes.submit(AppUserDetails(it), m, SubmitBallotResponseRequest(false)) }
        repeat(2) { val u = fixtures.createUser(emailPrefix = "map-dee$it"); vote(d2, u, "Dee"); measureVotes.submit(AppUserDetails(u), m, SubmitBallotResponseRequest(true)) }

        // A super: live, everything.
        val sup = AppUserDetails(fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "map-super"))
        val live = map.results("nd", m, sup)
        assertThat(live.stateName).isEqualTo("North Dakota")
        assertThat(live.live).isTrue()
        assertThat(live.measures.map { it.title }).containsExactly("ND Measure 1")
        assertThat(live.districts.map { it.number }).containsExactly(1, 2)
        val one = live.districts[0]
        assertThat(one.candidates.map { Triple(it.name, it.party, it.pct) })
            .containsExactly(Triple("Ann", Party.DEM, 75), Triple("Bob", Party.REP, 25), Triple("Cy", Party.IND, 0))
        assertThat(one.candidates[0].votes).isEqualTo(3)
        assertThat(one.measure).isEqualTo(MapMeasureSplitDto(75, 25))
        assertThat(live.districts[1].candidates.map { it.party }).containsExactly(Party.DEM, Party.IND)
        assertThat(live.districts[1].measure).isEqualTo(MapMeasureSplitDto(100, 0))

        // The public: District 1 at its last step of 3 (Ann's three), District 2 hidden; its measure split withheld.
        val pub = map.results("ND", m)
        assertThat(pub.live).isFalse()
        assertThat(pub.districts[0].shown).isTrue()
        assertThat(pub.districts[0].candidates.first().let { it.name to it.pct }).isEqualTo("Ann" to 100)
        assertThat(pub.districts[0].candidates.first().votes).isNull()
        assertThat(pub.districts[0].measure).isNotNull()
        assertThat(pub.districts[1].shown).isFalse()
        assertThat(pub.districts[1].candidates.map { it.pct }).containsOnlyNulls()
        assertThat(pub.districts[1].measure).isNull()
    }
}
