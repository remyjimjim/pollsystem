package org.kodewerks.pollsystem.admin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.CreatorRequest
import org.kodewerks.pollsystem.model.RequestStatus
import org.kodewerks.pollsystem.model.RoleAssignment
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.CreatorRequestRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.kodewerks.pollsystem.repository.StateRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired

/** The admin dashboard works for statewide (and nationwide) admins, not just zipcode ones. */
class AdminDashboardControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var controller: AdminDashboardController
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var roleAssignments: RoleAssignmentRepository
    @Autowired private lateinit var creatorRequests: CreatorRequestRepository
    @Autowired private lateinit var states: StateRepository

    private fun grant(u: User, role: AccessLevel, level: ScopeLevel, initial: String? = null, request: CreatorRequest? = null, enabled: Boolean = true) =
        roleAssignments.save(RoleAssignment(user = u, role = role, scopeLevel = level,
            state = initial?.let { states.findByInitial(it)!! }, enabled = enabled, creatorRequest = request))

    /** A pending, unassigned creator request for [initial] (or nationwide). */
    private fun unassignedRequest(prefix: String, initial: String?): Long {
        val u = fixtures.createUser(emailPrefix = prefix)
        val req = creatorRequests.save(CreatorRequest(user = u, reason = "r", status = RequestStatus.PENDING))
        grant(u, AccessLevel.CREATOR, if (initial == null) ScopeLevel.NATIONAL else ScopeLevel.STATE, initial, req, enabled = false)
        return req.id
    }

    @Test
    fun `a statewide admin sees their area, unassigned requests there, and creators in scope`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "dash-ca")
        grant(admin, AccessLevel.ADMIN, ScopeLevel.STATE, "CA")
        val inCa = unassignedRequest("dash-req-ca", "CA")
        val inTx = unassignedRequest("dash-req-tx", "TX")
        val national = unassignedRequest("dash-req-us", null)
        val caCreator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "dash-c", nationwideCreatorGrant = false)
        grant(caCreator, AccessLevel.CREATOR, ScopeLevel.STATE, "CA")
        val txCreator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "dash-t", nationwideCreatorGrant = false)
        grant(txCreator, AccessLevel.CREATOR, ScopeLevel.STATE, "TX")

        val d = controller.dashboard(AppUserDetails(admin))
        assertThat(d.areas).containsExactly("California")
        assertThat(d.unassignedInScope.map { it.id }).contains(inCa, national).doesNotContain(inTx)
        val before = d.creatorsInScopeCount
        assertThat(before).isGreaterThanOrEqualTo(1)

        // The TX creator isn't counted; another CA creator is.
        val another = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "dash-c2", nationwideCreatorGrant = false)
        grant(another, AccessLevel.CREATOR, ScopeLevel.STATE, "CA")
        assertThat(controller.dashboard(AppUserDetails(admin)).creatorsInScopeCount).isEqualTo(before + 1)
    }

    @Test
    fun `a nationwide admin sees every unassigned request`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "dash-us")
        grant(admin, AccessLevel.ADMIN, ScopeLevel.NATIONAL)
        val inTx = unassignedRequest("dash-req-tx", "TX")

        val d = controller.dashboard(AppUserDetails(admin))
        assertThat(d.areas).containsExactly("Nationwide")
        assertThat(d.unassignedInScope.map { it.id }).contains(inTx)
    }
}
