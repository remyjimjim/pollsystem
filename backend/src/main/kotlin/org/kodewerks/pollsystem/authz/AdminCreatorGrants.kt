package org.kodewerks.pollsystem.authz

import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.RoleAssignment
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.springframework.stereotype.Component

/**
 * Access is additive: an admin is also a creator across their admin area. Poll
 * creation counts CREATOR grants only (CreatorGrantGuard), so whenever ADMIN
 * grants are enabled, matching CREATOR grants are added: same scope and region,
 * every poll type, enabled, not tied to a creator request. They're ordinary
 * creator grants, so the admin (or another admin) can switch them off on
 * Manage Creators. A region where the user already has any CREATOR grant,
 * enabled or not, is left alone, so a deliberate disable isn't undone.
 * V27 applied the same rule to existing admins.
 */
@Component
class AdminCreatorGrants(private val roleAssignments: RoleAssignmentRepository) {

    fun mirror(user: User, adminGrants: List<RoleAssignment>) {
        val existing = roleAssignments.findByUserIdAndRole(user.id, AccessLevel.CREATOR)
        val toAdd = adminGrants
            .filter { it.role == AccessLevel.ADMIN && it.enabled }
            .filter { a -> existing.none { sameRegion(it, a) } }
            .distinctBy { listOf(it.scopeLevel, it.state?.id, it.county?.id, it.zipcode) }
            .map {
                RoleAssignment(
                    user = user, role = AccessLevel.CREATOR, scopeLevel = it.scopeLevel,
                    state = it.state, county = it.county, zipcode = it.zipcode, enabled = true
                )
            }
        if (toAdd.isNotEmpty()) roleAssignments.saveAll(toAdd)
    }

    private fun sameRegion(a: RoleAssignment, b: RoleAssignment) =
        a.scopeLevel == b.scopeLevel && a.state?.id == b.state?.id &&
            a.county?.id == b.county?.id && a.zipcode == b.zipcode
}
