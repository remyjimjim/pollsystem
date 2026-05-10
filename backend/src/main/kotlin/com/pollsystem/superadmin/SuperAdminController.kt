package com.pollsystem.superadmin

import com.pollsystem.model.AccessLevel
import com.pollsystem.repository.RoleAssignmentRepository
import com.pollsystem.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class AdminRoleAssignmentDto(
    val id: Long,
    val zipcode: String,
    val countyName: String,
    val stateInitial: String,
    val enabled: Boolean
)

data class AdminUserDto(
    val id: Long,
    val email: String,
    val phone: String,
    val isEnabled: Boolean,
    val access: AccessLevel,
    val roleAssignments: List<AdminRoleAssignmentDto>
)

@RestController
@RequestMapping("/api/super/admins")
class SuperAdminController(
    private val users: UserRepository,
    private val roleAssignments: RoleAssignmentRepository
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun list(): List<AdminUserDto> {
        val admins = users.findByAccess(AccessLevel.ADMIN) +
            users.findByAccess(AccessLevel.SUPER)
        if (admins.isEmpty()) return emptyList()
        val rowsByUser = roleAssignments
            .findByUserIdInAndRole(admins.map { it.id }, AccessLevel.ADMIN)
            .groupBy { it.user.id }
        return admins.sortedBy { it.email }.map { u ->
            val rows = rowsByUser[u.id].orEmpty().sortedBy { it.zipcode }
            AdminUserDto(
                id = u.id,
                email = u.email,
                phone = u.phone,
                isEnabled = u.isEnabled,
                access = u.access,
                roleAssignments = rows.map {
                    AdminRoleAssignmentDto(
                        id = it.id,
                        zipcode = it.zipcode,
                        countyName = it.county.name,
                        stateInitial = it.state.initial,
                        enabled = it.enabled
                    )
                }
            )
        }
    }

    @PostMapping("/role-assignments/{raId}/toggle")
    @Transactional
    fun toggleAssignment(@PathVariable raId: Long): AdminRoleAssignmentDto {
        val ra = roleAssignments.findById(raId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Role assignment not found")
        }
        if (ra.role != AccessLevel.ADMIN) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Only ADMIN role assignments are managed here"
            )
        }
        val saved = roleAssignments.save(ra.copy(enabled = !ra.enabled))
        return AdminRoleAssignmentDto(
            id = saved.id,
            zipcode = saved.zipcode,
            countyName = saved.county.name,
            stateInitial = saved.state.initial,
            enabled = saved.enabled
        )
    }

    @PostMapping("/{userId}/demote")
    @Transactional
    fun demote(@PathVariable userId: Long): AdminUserDto {
        val u = users.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        if (u.access == AccessLevel.SUPER) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot demote a Super")
        }
        if (u.access.ordinal < AccessLevel.ADMIN.ordinal) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User is not an Admin")
        }
        val updated = users.save(u.copy(access = AccessLevel.CREATOR))
        val rows = roleAssignments.findByUserIdAndRole(userId, AccessLevel.ADMIN)
        val savedRows = roleAssignments.saveAll(rows.map { it.copy(enabled = false) })
        // Build the DTO directly: list() filters to ADMIN+SUPER, and we just
        // demoted this user out of that set.
        return AdminUserDto(
            id = updated.id,
            email = updated.email,
            phone = updated.phone,
            isEnabled = updated.isEnabled,
            access = updated.access,
            roleAssignments = savedRows.sortedBy { it.zipcode }.map {
                AdminRoleAssignmentDto(
                    id = it.id,
                    zipcode = it.zipcode,
                    countyName = it.county.name,
                    stateInitial = it.state.initial,
                    enabled = it.enabled
                )
            }
        )
    }
}
