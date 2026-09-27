package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.BallotMeasure
import org.kodewerks.pollsystem.model.Election
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollPurview
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.model.PollType
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.PollPurviewRepository
import org.kodewerks.pollsystem.repository.PollTypeRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate

class PollPurviewServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: PollPurviewService
    @Autowired private lateinit var purviews: PollPurviewRepository
    @Autowired private lateinit var countyZips: CountyZipsRepository
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var elections: ElectionRepository
    @Autowired private lateinit var ballotMeasures: BallotMeasureRepository
    @Autowired private lateinit var pollTypes: PollTypeRepository

    // 90001 = Los Angeles County, CA. 10001 = New York (different county AND state).
    private val laCountyId: Long by lazy { countyZips.findByZipcode("90001").first().county.id }
    private val caStateId: Long by lazy { countyZips.findByZipcode("90001").first().county.state.id }

    private fun row(level: ScopeLevel, stateId: Long? = null, countyId: Long? = null, zip: String? = null) =
        PollPurview(
            pollType = PollKind.ELECTION, pollId = 0, scopeLevel = level,
            stateId = stateId, countyId = countyId, zipcode = zip
        )

    @Test
    fun `NATIONAL purview includes every zipcode and even a null zip`() {
        val rows = listOf(row(ScopeLevel.NATIONAL))
        assertThat(service.includesZip(rows, "90001")).isTrue()
        assertThat(service.includesZip(rows, "10001")).isTrue()
        assertThat(service.includesZip(rows, null)).isTrue()
    }

    @Test
    fun `STATE purview includes in-state zips only`() {
        val rows = listOf(row(ScopeLevel.STATE, stateId = caStateId))
        assertThat(service.includesZip(rows, "90001")).isTrue()   // CA
        assertThat(service.includesZip(rows, "10001")).isFalse()  // NY
        assertThat(service.includesZip(rows, null)).isFalse()     // can't place a null zip sub-nationally
    }

    @Test
    fun `COUNTY purview includes in-county zips only`() {
        val rows = listOf(row(ScopeLevel.COUNTY, countyId = laCountyId))
        assertThat(service.includesZip(rows, "90001")).isTrue()
        assertThat(service.includesZip(rows, "10001")).isFalse()
    }

    @Test
    fun `ZIP purview includes the exact zip only`() {
        val rows = listOf(row(ScopeLevel.ZIP, zip = "90001"))
        assertThat(service.includesZip(rows, "90001")).isTrue()
        assertThat(service.includesZip(rows, "90002")).isFalse()
        assertThat(service.includesZip(rows, "10001")).isFalse()
    }

    @Test
    fun `a poll with no purview rows is treated as nationwide`() {
        assertThat(service.includesZip(emptyList(), "90001")).isTrue()
        assertThat(service.includesZip(emptyList(), null)).isTrue()
    }

    @Test
    fun `a ballot measure inherits its election's purview`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "purview-bm")
        val electionType: PollType = pollTypes.findAll().first { it.name == "Election" }
        val election = elections.save(
            Election(
                creator = creator, pollType = electionType, title = "BM host",
                date = LocalDate.now(), zipcode = "90001", status = PollStatus.PUBLISHED
            )
        )
        purviews.save(
            PollPurview(
                pollType = PollKind.ELECTION, pollId = election.id,
                scopeLevel = ScopeLevel.STATE, stateId = caStateId
            )
        )
        val bm = ballotMeasures.save(
            BallotMeasure(
                creator = creator, pollType = electionType, title = "Measure A",
                summary = "summary", election = election, effectiveDate = LocalDate.now(),
                status = PollStatus.PUBLISHED, closeDate = Instant.now().plusSeconds(86_400)
            )
        )

        assertThat(service.purviewOf(PollKind.BALLOT_MEASURE, bm.id).map { it.scopeLevel })
            .containsExactly(ScopeLevel.STATE)
        assertThat(service.includesZip(PollKind.BALLOT_MEASURE, bm.id, "90001")).isTrue()
        assertThat(service.includesZip(PollKind.BALLOT_MEASURE, bm.id, "10001")).isFalse()
    }

    @Test
    fun `replacePurview writes ZIP rows and replaces them on re-call`() {
        service.replacePurview(PollKind.ELECTION, 987_001L, ScopeLevel.ZIP, zipcodes = listOf("90001", "10001"))
        assertThat(purviews.findByPollTypeAndPollId(PollKind.ELECTION, 987_001L))
            .allMatch { it.scopeLevel == ScopeLevel.ZIP }
            .hasSize(2)

        // Re-call replaces (delete-then-insert), not appends.
        service.replacePurview(PollKind.ELECTION, 987_001L, ScopeLevel.ZIP, zipcodes = listOf("90001"))
        assertThat(purviews.findByPollTypeAndPollId(PollKind.ELECTION, 987_001L)).hasSize(1)
    }

    @Test
    fun `replacePurview fans STATE, COUNTY, and NATIONAL selections into the right columns`() {
        service.replacePurview(PollKind.QUESTIONNAIRE, 987_002L, ScopeLevel.STATE, regionIds = listOf(caStateId))
        purviews.findByPollTypeAndPollId(PollKind.QUESTIONNAIRE, 987_002L).single().let {
            assertThat(it.scopeLevel).isEqualTo(ScopeLevel.STATE)
            assertThat(it.stateId).isEqualTo(caStateId)
            assertThat(it.countyId).isNull()
            assertThat(it.zipcode).isNull()
        }

        service.replacePurview(PollKind.QUESTIONNAIRE, 987_002L, ScopeLevel.COUNTY, regionIds = listOf(laCountyId))
        purviews.findByPollTypeAndPollId(PollKind.QUESTIONNAIRE, 987_002L).single().let {
            assertThat(it.scopeLevel).isEqualTo(ScopeLevel.COUNTY)
            assertThat(it.countyId).isEqualTo(laCountyId)
        }

        service.replacePurview(PollKind.QUESTIONNAIRE, 987_002L, ScopeLevel.NATIONAL)
        purviews.findByPollTypeAndPollId(PollKind.QUESTIONNAIRE, 987_002L).single().let {
            assertThat(it.scopeLevel).isEqualTo(ScopeLevel.NATIONAL)
            assertThat(it.stateId).isNull()
            assertThat(it.countyId).isNull()
            assertThat(it.zipcode).isNull()
        }
    }

    @Test
    fun `classifyZips batch-labels each zip within or outside`() {
        val countyRows = listOf(row(ScopeLevel.COUNTY, countyId = laCountyId))
        val m = service.classifyZips(countyRows, listOf("90001", "10001", null))
        assertThat(m["90001"]).isTrue()
        assertThat(m["10001"]).isFalse()
        assertThat(m[null]).isFalse()

        // NATIONAL (or no rows) → everyone within, even a null zip.
        assertThat(service.classifyZips(listOf(row(ScopeLevel.NATIONAL)), listOf("10001", null)).values)
            .containsOnly(true)
        assertThat(service.classifyZips(emptyList(), listOf("10001")).values).containsOnly(true)
    }

    @Test
    fun `filterByPurview keeps only the selected within-outside group`() {
        val purview = listOf(row(ScopeLevel.STATE, stateId = caStateId)) // CA
        val zips = listOf("90001", "10001") // CA, NY

        assertThat(filterByPurview(zips, { it }, purview, withinPurview = true, outsidePurview = false, service))
            .containsExactly("90001")
        assertThat(filterByPurview(zips, { it }, purview, withinPurview = false, outsidePurview = true, service))
            .containsExactly("10001")
        assertThat(filterByPurview(zips, { it }, purview, withinPurview = true, outsidePurview = true, service))
            .containsExactlyInAnyOrder("90001", "10001")
    }

    @Test
    fun `replacePurview rejects a ballot measure, unknown zips, and empty selections`() {
        assertThatThrownBy { service.replacePurview(PollKind.BALLOT_MEASURE, 1L, ScopeLevel.NATIONAL) }
            .isInstanceOf(ResponseStatusException::class.java)
        assertThatThrownBy { service.replacePurview(PollKind.ELECTION, 987_003L, ScopeLevel.ZIP, zipcodes = listOf("00000")) }
            .isInstanceOf(ResponseStatusException::class.java)
        assertThatThrownBy { service.replacePurview(PollKind.ELECTION, 987_003L, ScopeLevel.STATE, regionIds = emptyList()) }
            .isInstanceOf(ResponseStatusException::class.java)
    }
}
