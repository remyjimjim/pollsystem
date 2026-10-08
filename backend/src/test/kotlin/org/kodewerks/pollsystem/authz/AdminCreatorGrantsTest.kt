package org.kodewerks.pollsystem.authz

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.RoleAssignment
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.kodewerks.pollsystem.repository.StateRepository
import org.springframework.beans.factory.annotation.Autowired

/** Admins get CREATOR grants mirroring their enabled ADMIN grants (access is additive). */
class AdminCreatorGrantsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mirror: AdminCreatorGrants
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var roleAssignments: RoleAssignmentRepository
    @Autowired private lateinit var states: StateRepository

    private fun adminGrant(user: org.kodewerks.pollsystem.model.User, initial: String, enabled: Boolean = true) =
        roleAssignments.save(RoleAssignment(user = user, role = AccessLevel.ADMIN, scopeLevel = ScopeLevel.STATE,
            state = states.findByInitial(initial)!!, enabled = enabled))

    private fun creatorGrants(userId: Long) = roleAssignments.findByUserIdAndRole(userId, AccessLevel.CREATOR)

    @Test
    fun `mirrors each enabled admin grant as an enabled, all-types creator grant`() {
        val a = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "acg")
        mirror.mirror(a, listOf(adminGrant(a, "CA"), adminGrant(a, "NV"), adminGrant(a, "TX", enabled = false)))

        val gs = creatorGrants(a.id)
        assertThat(gs.map { it.state?.initial }).containsExactlyInAnyOrder("CA", "NV")
        assertThat(gs).allSatisfy {
            assertThat(it.enabled).isTrue()
            assertThat(it.pollType).isNull()
            assertThat(it.scopeLevel).isEqualTo(ScopeLevel.STATE)
            assertThat(it.creatorRequest).isNull()
        }
    }

    @Test
    fun `leaves a region alone when a creator grant already exists there, even a disabled one`() {
        val a = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "acg")
        val ca = states.findByInitial("CA")!!
        roleAssignments.save(RoleAssignment(user = a, role = AccessLevel.CREATOR, scopeLevel = ScopeLevel.STATE, state = ca, enabled = false))

        mirror.mirror(a, listOf(adminGrant(a, "CA")))
        mirror.mirror(a, listOf(adminGrant(a, "CA"))) // idempotent

        val gs = creatorGrants(a.id)
        assertThat(gs).hasSize(1)
        assertThat(gs.single().enabled).isFalse() // a deliberate disable isn't undone
    }
}
