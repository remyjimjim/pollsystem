package org.kodewerks.pollsystem.creatorrequest

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.repository.CreatorRequestRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Unit-tests the service's `unassignStale(threshold)` method directly. The
 * @Scheduled job in StaleCreatorRequestSweeper is essentially a thin wrapper
 * around this call with `Instant.now().minus(48h)`. Disabled in tests via
 * the test profile's 1-hour sweep interval.
 */
class StaleRequestSweeperTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: CreatorRequestService
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var creatorRequests: CreatorRequestRepository

    @Test
    fun `unassignStale clears assigned admin for old PENDING requests`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "admin")
        fixtures.assignAdmin(admin)
        val applicant = fixtures.createUser(emailPrefix = "applicant")

        val req = service.submit(
            applicant,
            SubmitCreatorRequest(
                pollTypeIds = listOf(1L),
                zipcodes = listOf("90001"),
                reason = "Reason"
            )
        )
        // Request is initially assigned
        assertThat(creatorRequests.findById(req.id).orElseThrow().assignedAdmin).isNotNull

        // A threshold "in the future" makes every PENDING look stale
        val futureThreshold = Instant.now().plus(1, ChronoUnit.HOURS)
        val unassigned = service.unassignStale(futureThreshold)
        assertThat(unassigned).isEqualTo(1)

        val refreshed = creatorRequests.findById(req.id).orElseThrow()
        assertThat(refreshed.assignedAdmin).isNull()
    }

    @Test
    fun `unassignStale ignores requests submitted after the threshold`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "admin")
        fixtures.assignAdmin(admin)
        val applicant = fixtures.createUser(emailPrefix = "applicant")

        val req = service.submit(
            applicant,
            SubmitCreatorRequest(
                pollTypeIds = listOf(1L),
                zipcodes = listOf("90001"),
                reason = "Reason"
            )
        )

        // Threshold deep in the past — the request is "fresh"
        val pastThreshold = Instant.now().minus(7, ChronoUnit.DAYS)
        val unassigned = service.unassignStale(pastThreshold)
        assertThat(unassigned).isEqualTo(0)
        assertThat(creatorRequests.findById(req.id).orElseThrow().assignedAdmin).isNotNull
    }

    @Test
    fun `unassignStale leaves already-unassigned requests alone (idempotent)`() {
        // Submit with no admin available — request starts unassigned
        val applicant = fixtures.createUser(emailPrefix = "applicant")
        val req = service.submit(
            applicant,
            SubmitCreatorRequest(
                pollTypeIds = listOf(1L),
                zipcodes = listOf("90001"),
                reason = "Reason"
            )
        )
        assertThat(req.assignedAdmin).isNull()

        val futureThreshold = Instant.now().plus(1, ChronoUnit.HOURS)
        val touched = service.unassignStale(futureThreshold)
        // unassignStale only counts requests that *had* an admin assigned
        assertThat(touched).isEqualTo(0)
    }
}
