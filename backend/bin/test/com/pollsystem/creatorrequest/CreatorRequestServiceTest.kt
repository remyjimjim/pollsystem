package com.pollsystem.creatorrequest

import com.pollsystem.AbstractIntegrationTest
import com.pollsystem.TestFixtures
import com.pollsystem.model.AccessLevel
import com.pollsystem.model.RequestStatus
import com.pollsystem.repository.CreatorRequestRepository
import com.pollsystem.repository.RoleAssignmentRepository
import com.pollsystem.repository.UserRepository
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
                zipcodes = listOf("90001"),
                reason = "I want to host local polls."
            )
        )

        assertThat(request.status).isEqualTo(RequestStatus.PENDING)
        assertThat(request.assignedAdmin?.id).isEqualTo(admin.id)

        val rows = roleAssignments.findByCreatorRequestId(request.id)
        // 1 zipcode × 2 poll types = 2 rows, all enabled=false
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
                zipcodes = listOf("90001"),
                reason = "Reason"
            )
        )

        service.batchApprove(listOf(req.id))

        val updated = creatorRequests.findById(req.id).orElseThrow()
        assertThat(updated.status).isEqualTo(RequestStatus.APPROVED)
        assertThat(updated.processedAt).isNotNull

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
                zipcodes = listOf("90001"),
                reason = "Reason"
            )
        )

        service.batchReject(listOf(req.id))

        val updated = creatorRequests.findById(req.id).orElseThrow()
        assertThat(updated.status).isEqualTo(RequestStatus.REJECTED)

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
                zipcodes = listOf("90001"),
                reason = "No admin to route to"
            )
        )

        assertThat(req.assignedAdmin).isNull()
        assertThat(req.status).isEqualTo(RequestStatus.PENDING)
    }
}
