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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
}
