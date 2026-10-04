package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.BlockScope
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollTypeBlock
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.PollTypeBlockRepository
import org.kodewerks.pollsystem.repository.StateRepository
import org.springframework.beans.factory.annotation.Autowired

/** Blocks apply only inside their area; EVERYWHERE shuts the poll (and its results) for all. */
class AreaAwareBlocksTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: PollBlockService
    @Autowired private lateinit var questionnaires: QuestionnaireService
    @Autowired private lateinit var blocks: PollTypeBlockRepository
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var states: StateRepository
    @Autowired private lateinit var counties: CountyRepository

    private val caId get() = states.findByInitial("CA")!!.id
    private val laId get() = counties.findByStateId(caId).first { it.name == "Los Angeles" }.id

    private val blocker by lazy { fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "areablocker") }

    /** A nationwide questionnaire (so any respondent is inside its purview). */
    private fun poll(): Long {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "areablock")
        return questionnaires.saveDraft(
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = 2L, title = "Area", summary = "s",
                questions = listOf(QuestionInput("Q?")), scopeLevel = ScopeLevel.NATIONAL
            )
        ).id
    }
    private fun block(pollId: Long, scope: BlockScope, zip: String? = null, countyId: Long? = null, stateId: Long? = null) =
        blocks.save(PollTypeBlock(pollType = PollKind.QUESTIONNAIRE, pollId = pollId, scope = scope,
            zipcode = zip, countyId = countyId, stateId = stateId, createdBy = blocker.id))
    private fun blockedAt(pollId: Long, zip: String?) = service.isBlockedFor(PollKind.QUESTIONNAIRE, pollId, zip)

    @Test
    fun `a zip block stops only respondents in that zip`() {
        val id = poll()
        block(id, BlockScope.ZIPCODE, zip = "90001")
        assertThat(blockedAt(id, "90001")).isTrue()
        assertThat(blockedAt(id, "90210")).isFalse()
        assertThat(blockedAt(id, null)).isFalse()
    }

    @Test
    fun `county and state blocks cover the zips inside them`() {
        val county = poll().also { block(it, BlockScope.COUNTY, countyId = laId) }
        assertThat(blockedAt(county, "90001")).isTrue()   // Los Angeles
        assertThat(blockedAt(county, "94110")).isFalse()  // San Francisco

        val state = poll().also { block(it, BlockScope.STATE, stateId = caId) }
        assertThat(blockedAt(state, "94110")).isTrue()
        assertThat(blockedAt(state, "10001")).isFalse()   // New York
    }

    @Test
    fun `an everywhere block stops everyone and hides results, area blocks don't hide results`() {
        val everywhere = poll().also { block(it, BlockScope.EVERYWHERE) }
        assertThat(blockedAt(everywhere, "10001")).isTrue()
        assertThat(blockedAt(everywhere, null)).isTrue()
        assertThat(service.isBlockedEverywhere(PollKind.QUESTIONNAIRE, everywhere)).isTrue()

        val area = poll().also { block(it, BlockScope.STATE, stateId = caId) }
        assertThat(service.isBlockedEverywhere(PollKind.QUESTIONNAIRE, area)).isFalse()
    }

    @Test
    fun `search hides a poll only where every searched zip is blocked`() {
        val id = poll().also { block(it, BlockScope.ZIPCODE, zip = "90001") }
        fun visible(zips: List<String>?) =
            service.filterUnblocked(listOf(id), { PollKind.QUESTIONNAIRE }, { it }, zips).isNotEmpty()

        assertThat(visible(null)).isTrue()
        assertThat(visible(listOf("90001"))).isFalse()
        assertThat(visible(listOf("90001", "90210"))).isTrue()
    }
}
