package com.pollsystem.superadmin

import com.pollsystem.AbstractIntegrationTest
import com.pollsystem.TestFixtures
import com.pollsystem.model.AccessLevel
import com.pollsystem.repository.CountyRepository
import com.pollsystem.repository.RoleAssignmentRepository
import com.pollsystem.repository.StateRepository
import com.pollsystem.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException

class SuperAdminControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var controller: SuperAdminController
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var roleAssignments: RoleAssignmentRepository
    @Autowired private lateinit var states: StateRepository
    @Autowired private lateinit var counties: CountyRepository

    @Test
    fun `list returns admins with their role assignments`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "admin")
        val ra = fixtures.assignAdmin(admin)

        val all = controller.list()
        val mine = all.first { it.id == admin.id }
        assertThat(mine.access).isEqualTo(AccessLevel.ADMIN)
        assertThat(mine.roleAssignments).hasSize(1)
        assertThat(mine.roleAssignments[0].id).isEqualTo(ra.id)
        assertThat(mine.roleAssignments[0].zipcode).isEqualTo("90001")
        assertThat(mine.roleAssignments[0].enabled).isTrue
    }

    @Test
    fun `list also includes SUPER users`() {
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "super")
        val all = controller.list()
        assertThat(all.map { it.id }).contains(sup.id)
        assertThat(all.first { it.id == sup.id }.access).isEqualTo(AccessLevel.SUPER)
    }

    @Test
    fun `toggleAssignment flips enabled flag`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "admin")
        val ra = fixtures.assignAdmin(admin)
        assertThat(ra.enabled).isTrue

        val afterFirst = controller.toggleAssignment(ra.id)
        assertThat(afterFirst.enabled).isFalse

        val afterSecond = controller.toggleAssignment(ra.id)
        assertThat(afterSecond.enabled).isTrue
    }

    @Test
    fun `demote sets access to CREATOR and disables admin RAs`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "admin")
        fixtures.assignAdmin(admin)

        val result = controller.demote(admin.id)
        assertThat(result.access).isEqualTo(AccessLevel.CREATOR)

        val refreshed = users.findById(admin.id).orElseThrow()
        assertThat(refreshed.access).isEqualTo(AccessLevel.CREATOR)

        val rows = roleAssignments.findByUserIdAndRole(admin.id, AccessLevel.ADMIN)
        assertThat(rows).allMatch { !it.enabled }
    }

    @Test
    fun `demoting a SUPER is forbidden`() {
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "super")

        assertThatThrownBy { controller.demote(sup.id) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(403)
            }

        val refreshed = users.findById(sup.id).orElseThrow()
        assertThat(refreshed.access).isEqualTo(AccessLevel.SUPER)
    }

    @Test
    fun `demoting a non-admin user is a 409`() {
        val user = fixtures.createUser(emailPrefix = "user")  // USER access

        assertThatThrownBy { controller.demote(user.id) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(409)
            }
    }

    @Test
    fun `toggling a non-ADMIN role assignment is a 400`() {
        val user = fixtures.createUser(emailPrefix = "user")
        val ca = states.findByInitial("CA")!!
        val la = counties.findByStateId(ca.id).first { it.name == "Los Angeles" }
        val creatorRow = roleAssignments.save(
            com.pollsystem.model.RoleAssignment(
                user = user,
                role = AccessLevel.CREATOR,
                state = ca,
                county = la,
                zipcode = "90001",
                enabled = true
            )
        )

        assertThatThrownBy { controller.toggleAssignment(creatorRow.id) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(400)
            }
    }
}
