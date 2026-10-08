package org.kodewerks.pollsystem.creatorrequest

import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.RequestStatus
import org.kodewerks.pollsystem.repository.CreatorRequestRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CreatorRequestServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: CreatorRequestService
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var creatorRequests: CreatorRequestRepository
    @Autowired private lateinit var roleAssignments: RoleAssignmentRepository
    @Autowired private lateinit var users: UserRepository

    @Test
    fun `submit creates request, role assignments, and routes to least-loaded admin`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "admin")
        fixtures.assignAdmin(admin)
        val applicant = fixtures.createUser()

        val request = service.submit(
            applicant,
            SubmitCreatorRequest(
                pollTypeIds = listOf(1L, 2L),  // Election + Questionnaire from V1 seed
                scopeLevel = ScopeLevel.STATE, regionIds = listOf(fixtures.stateId("CA")),
                reason = "I want to host local polls."
            )
        )

        assertThat(request.status).isEqualTo(RequestStatus.PENDING)
        assertThat(request.assignedAdmin?.id).isEqualTo(admin.id)

        val rows = roleAssignments.findByCreatorRequestId(request.id)
        // 1 state × 2 poll types = 2 rows, all enabled=false
        assertThat(rows).hasSize(2)
        assertThat(rows).allMatch { !it.enabled }
        assertThat(rows.map { it.pollType?.id }.toSet()).containsExactlyInAnyOrder(1L, 2L)
    }

    @Test
    fun `batch approve enables role assignments and bumps user access`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "admin")
        fixtures.assignAdmin(admin)
        val applicant = fixtures.createUser()

        val req = service.submit(
            applicant,
            SubmitCreatorRequest(
                pollTypeIds = listOf(1L),
                scopeLevel = ScopeLevel.STATE, regionIds = listOf(fixtures.stateId("CA")),
                reason = "Reason"
            )
        )

        service.batchApprove(listOf(req.id), admin)

        val updated = creatorRequests.findById(req.id).orElseThrow()
        assertThat(updated.status).isEqualTo(RequestStatus.APPROVED)
        assertThat(updated.processedAt).isNotNull
        assertThat(updated.processedBy?.id).isEqualTo(admin.id)

        val rows = roleAssignments.findByCreatorRequestId(req.id)
        assertThat(rows).allMatch { it.enabled }

        val refreshedUser = users.findById(applicant.id).orElseThrow()
        assertThat(refreshedUser.access).isEqualTo(AccessLevel.CREATOR)
    }

    @Test
    fun `batch reject leaves rows disabled and does not promote user`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "admin")
        fixtures.assignAdmin(admin)
        val applicant = fixtures.createUser()

        val req = service.submit(
            applicant,
            SubmitCreatorRequest(
                pollTypeIds = listOf(1L),
                scopeLevel = ScopeLevel.STATE, regionIds = listOf(fixtures.stateId("CA")),
                reason = "Reason"
            )
        )

        service.batchReject(listOf(req.id), admin)

        val updated = creatorRequests.findById(req.id).orElseThrow()
        assertThat(updated.status).isEqualTo(RequestStatus.REJECTED)
        assertThat(updated.processedBy?.id).isEqualTo(admin.id)

        val rows = roleAssignments.findByCreatorRequestId(req.id)
        assertThat(rows).allMatch { !it.enabled }

        val refreshedUser = users.findById(applicant.id).orElseThrow()
        assertThat(refreshedUser.access).isEqualTo(AccessLevel.USER)
    }

    @Test
    fun `submit without matching admin leaves request unassigned`() {
        // No admin created — applicant submits for a zip with no enabled admin
        val applicant = fixtures.createUser()

        val req = service.submit(
            applicant,
            SubmitCreatorRequest(
                pollTypeIds = listOf(1L),
                scopeLevel = ScopeLevel.STATE, regionIds = listOf(fixtures.stateId("CA")),
                reason = "No admin to route to"
            )
        )

        assertThat(req.assignedAdmin).isNull()
        assertThat(req.status).isEqualTo(RequestStatus.PENDING)
    }

    @Test
    fun `creator access is requested statewide or nationwide only`() {
        val applicant = fixtures.createUser()
        for (bad in listOf(
            SubmitCreatorRequest(pollTypeIds = listOf(2L), scopeLevel = ScopeLevel.ZIP, zipcodes = listOf("90001")),
            SubmitCreatorRequest(pollTypeIds = listOf(2L), scopeLevel = ScopeLevel.COUNTY, regionIds = listOf(1L)),
        )) {
            org.assertj.core.api.Assertions.assertThatThrownBy { service.submit(applicant, bad) }
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException::class.java) {
                    assertThat(it.statusCode.value()).isEqualTo(400)
                    assertThat(it.reason).contains("statewide or nationwide only")
                }
        }
        service.submit(applicant, SubmitCreatorRequest(pollTypeIds = listOf(2L), scopeLevel = ScopeLevel.NATIONAL))
    }
}
