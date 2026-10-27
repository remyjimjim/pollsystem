package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollPurview
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.model.Questionnaire
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.PollPurviewRepository
import org.kodewerks.pollsystem.repository.PollTypeRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Phase D: search surfaces a poll when the SEARCHED zip falls inside the poll's
 * purview (zip ∈ county ∈ state ∈ nation), read from poll_purviews.
 */
class PollSearchPurviewTest : AbstractIntegrationTest() {

    @Autowired private lateinit var controller: PollSearchController
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var questionnaires: QuestionnaireRepository
    @Autowired private lateinit var purviews: PollPurviewRepository
    @Autowired private lateinit var pollTypes: PollTypeRepository
    @Autowired private lateinit var counties: org.kodewerks.pollsystem.repository.CountyRepository
    @Autowired private lateinit var countyZips: CountyZipsRepository

    private val caStateId: Long by lazy { countyZips.findByZipcode("90001").first().county.state.id }

    private fun publishedQuestionnaire(title: String): Questionnaire {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "search-purview")
        val qType = pollTypes.findAll().first { it.name == "Questionnaire" }
        return questionnaires.save(
            Questionnaire(
                creator = creator, pollType = qType, title = title,
                summary = "s", status = PollStatus.PUBLISHED, closeDate = null
            )
        )
    }

    /** Polls inside the picked place, most specific pick wins (decided 2026-10-27). */
    @Test
    fun `a place finds the polls inside it, and each narrower pick shrinks the list`() {
        val wa = 48L; val idaho = 13L
        val whatcom = counties.findByStateIdIn(listOf(wa)).first { it.name == "Whatcom" }.id
        val king = counties.findByStateIdIn(listOf(wa)).first { it.name == "King" }.id
        val tag = System.nanoTime()
        fun poll(name: String, purview: (Long) -> PollPurview): Long {
            val q = publishedQuestionnaire("Place $tag $name"); purviews.save(purview(q.id)); return q.id
        }
        val zip98225 = poll("98225") { PollPurview(pollType = PollKind.QUESTIONNAIRE, pollId = it, scopeLevel = ScopeLevel.ZIP, zipcode = "98225") }
        val zip98226 = poll("98226") { PollPurview(pollType = PollKind.QUESTIONNAIRE, pollId = it, scopeLevel = ScopeLevel.ZIP, zipcode = "98226") }
        val whatcomCounty = poll("Whatcom") { PollPurview(pollType = PollKind.QUESTIONNAIRE, pollId = it, scopeLevel = ScopeLevel.COUNTY, countyId = whatcom) }
        val kingCounty = poll("King") { PollPurview(pollType = PollKind.QUESTIONNAIRE, pollId = it, scopeLevel = ScopeLevel.COUNTY, countyId = king) }
        val washington = poll("WA") { PollPurview(pollType = PollKind.QUESTIONNAIRE, pollId = it, scopeLevel = ScopeLevel.STATE, stateId = wa) }
        val idahoState = poll("ID") { PollPurview(pollType = PollKind.QUESTIONNAIRE, pollId = it, scopeLevel = ScopeLevel.STATE, stateId = idaho) }
        val nationwide = poll("USA") { PollPurview(pollType = PollKind.QUESTIONNAIRE, pollId = it, scopeLevel = ScopeLevel.NATIONAL) }

        fun found(zips: List<String>? = null, countyIds: List<Long>? = null, stateIds: List<Long>? = null) =
            controller.search("Place $tag", zips, countyIds, stateIds, null, null, null, false).map { it.id }.toSet()

        // State only: statewide, its counties' and its zipcodes' polls. Not Idaho, not nationwide.
        assertThat(found(stateIds = listOf(wa))).containsExactlyInAnyOrder(zip98225, zip98226, whatcomCounty, kingCounty, washington)
        // State + county, or the county alone: the county's polls and its zipcodes'.
        assertThat(found(stateIds = listOf(wa), countyIds = listOf(whatcom))).containsExactlyInAnyOrder(zip98225, zip98226, whatcomCounty)
        assertThat(found(countyIds = listOf(whatcom))).containsExactlyInAnyOrder(zip98225, zip98226, whatcomCounty)
        // Zipcodes (with or without state and county): only polls for exactly those zipcodes.
        assertThat(found(stateIds = listOf(wa), countyIds = listOf(whatcom), zips = listOf("98225"))).containsExactly(zip98225)
        assertThat(found(zips = listOf("98225"))).containsExactly(zip98225)
        assertThat(found(stateIds = listOf(wa), countyIds = listOf(whatcom), zips = listOf("98225", "98226"))).containsExactlyInAnyOrder(zip98225, zip98226)
        // No place: everything, nationwide included.
        assertThat(found()).containsExactlyInAnyOrder(zip98225, zip98226, whatcomCounty, kingCounty, washington, idahoState, nationwide)
    }
}
