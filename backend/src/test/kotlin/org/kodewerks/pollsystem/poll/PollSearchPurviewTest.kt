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

    @Test
    fun `a STATE-purview poll surfaces for an in-state zip and hides for an out-of-state zip`() {
        val q = publishedQuestionnaire("Purview Search ${System.nanoTime()}")
        purviews.save(PollPurview(pollType = PollKind.QUESTIONNAIRE, pollId = q.id, scopeLevel = ScopeLevel.STATE, stateId = caStateId))

        val caHit = controller.search(null, listOf("90001"), null, null, null, null, null, false)
        assertThat(caHit.map { it.id }).contains(q.id)

        val nyHit = controller.search(null, listOf("10001"), null, null, null, null, null, false)
        assertThat(nyHit.map { it.id }).doesNotContain(q.id)
    }

    @Test
    fun `a NATIONAL-purview poll surfaces for any zip search`() {
        val q = publishedQuestionnaire("National Search ${System.nanoTime()}")
        purviews.save(PollPurview(pollType = PollKind.QUESTIONNAIRE, pollId = q.id, scopeLevel = ScopeLevel.NATIONAL))

        val nyHit = controller.search(null, listOf("10001"), null, null, null, null, null, false)
        assertThat(nyHit.map { it.id }).contains(q.id)
    }
}
