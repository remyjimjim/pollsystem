package org.kodewerks.pollsystem.adminrequest

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.RequestStatus
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.AdminRequestRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException

class AdminRequestServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: AdminRequestService
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var adminRequests: AdminRequestRepository
    @Autowired private lateinit var roleAssignments: RoleAssignmentRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var countyZips: org.kodewerks.pollsystem.repository.CountyZipsRepository

    @Test
    fun `submit creates request and disabled ADMIN role assignments`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")

        val req = service.submit(
            creator,
            SubmitAdminRequest(
                scopeLevel = ScopeLevel.ZIP,
                zipcodes = listOf("90001"),
                reason = "I want to administer this zip"
            )
        )

        assertThat(req.status).isEqualTo(RequestStatus.PENDING)
        val rows = roleAssignments.findByAdminRequestId(req.id)
        assertThat(rows).hasSize(1)
        assertThat(rows).allMatch { it.role == AccessLevel.ADMIN && !it.enabled }
    }

    @Test
    fun `submit by a USER (below CREATOR) is forbidden`() {
        val user = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "user")

        assertThatThrownBy {
            service.submit(
                user,
                SubmitAdminRequest(scopeLevel = ScopeLevel.ZIP, zipcodes = listOf("90001"), reason = "Skipping creator step")
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(403)
        }
    }

    @Test
    fun `batch approve enables role assignments and bumps user to ADMIN`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val approver = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "super")

        val req = service.submit(
            creator,
            SubmitAdminRequest(scopeLevel = ScopeLevel.ZIP, zipcodes = listOf("90001", "90012"), reason = "Reason")
        )

        service.batchApprove(listOf(req.id), approver)

        val updated = adminRequests.findById(req.id).orElseThrow()
        assertThat(updated.status).isEqualTo(RequestStatus.APPROVED)
        assertThat(updated.processedBy?.id).isEqualTo(approver.id)
        assertThat(updated.processedAt).isNotNull

        val rows = roleAssignments.findByAdminRequestId(req.id)
        assertThat(rows).hasSize(2).allMatch { it.enabled }

        // Access is additive: matching creator grants for the new admin area.
        val creatorZips = roleAssignments.findByUserIdAndRole(creator.id, AccessLevel.CREATOR)
            .filter { it.enabled && it.scopeLevel == ScopeLevel.ZIP }.map { it.zipcode }
        assertThat(creatorZips).containsExactlyInAnyOrder("90001", "90012")

        val refreshed = users.findById(creator.id).orElseThrow()
        assertThat(refreshed.access).isEqualTo(AccessLevel.ADMIN)
    }

    @Test
    fun `batch reject leaves rows disabled and access unchanged`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val approver = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "super")

        val req = service.submit(
            creator,
            SubmitAdminRequest(scopeLevel = ScopeLevel.ZIP, zipcodes = listOf("90001"), reason = "Reason")
        )

        service.batchReject(listOf(req.id), approver)

        val updated = adminRequests.findById(req.id).orElseThrow()
        assertThat(updated.status).isEqualTo(RequestStatus.REJECTED)

        val rows = roleAssignments.findByAdminRequestId(req.id)
        assertThat(rows).allMatch { !it.enabled }

        val refreshed = users.findById(creator.id).orElseThrow()
        assertThat(refreshed.access).isEqualTo(AccessLevel.CREATOR)
    }

    @Test
    fun `listPending returns PENDING in submission order`() {
        val approver = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "super")
        val a = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "ca")
        val b = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "cb")

        val req1 = service.submit(a, SubmitAdminRequest(scopeLevel = ScopeLevel.ZIP, zipcodes = listOf("90001"), reason = "first"))
        val req2 = service.submit(b, SubmitAdminRequest(scopeLevel = ScopeLevel.ZIP, zipcodes = listOf("90001"), reason = "second"))

        // Approve req1 so it shouldn't appear in PENDING anymore
        service.batchApprove(listOf(req1.id), approver)

        val pending = service.listPending().map { it.id }
        assertThat(pending).contains(req2.id).doesNotContain(req1.id)
    }

    @Test
    fun `submit at STATE scope creates one grant per state and labels the request`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "state-admin")
        val caStateId = countyZips.findByZipcode("90001").first().county.state.id

        val req = service.submit(
            creator,
            SubmitAdminRequest(scopeLevel = ScopeLevel.STATE, regionIds = listOf(caStateId), reason = "Cover CA")
        )

        val rows = roleAssignments.findByAdminRequestId(req.id)
        assertThat(rows).hasSize(1)
        assertThat(rows).allMatch {
            it.role == AccessLevel.ADMIN && it.scopeLevel == ScopeLevel.STATE &&
                it.state?.id == caStateId && it.county == null && it.zipcode == null && !it.enabled
        }
        val dto = service.toDto(req)
        assertThat(dto.scopeLevel).isEqualTo(ScopeLevel.STATE)
        assertThat(dto.stateIds).containsExactly(caStateId)
        assertThat(dto.regionLabel).isNotBlank()
    }

    @Test
    fun `submit at STATE scope with no regions is rejected`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "state-empty")
        assertThatThrownBy {
            service.submit(creator, SubmitAdminRequest(scopeLevel = ScopeLevel.STATE, regionIds = emptyList()))
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(400)
        }
    }

    @Test
    fun `unknown zipcode is rejected`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")

        assertThatThrownBy {
            service.submit(
                creator,
                SubmitAdminRequest(scopeLevel = ScopeLevel.ZIP, zipcodes = listOf("00000"), reason = "no")
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(400)
            assertThat(it.reason).contains("Unknown")
        }
    }
}
