package org.kodewerks.pollsystem.superadmin

import org.kodewerks.pollsystem.authz.AdminCreatorGrants
import org.kodewerks.pollsystem.authz.RoleAuthCache
import org.kodewerks.pollsystem.authz.requireGrantLevel
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.RoleAssignment
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.State
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.kodewerks.pollsystem.repository.StateRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/** The area for access granted by a super's role change: whole states, or nationwide. */
data class GrantArea(val scopeLevel: ScopeLevel, val regionIds: List<Long> = emptyList())

/**
 * A super changing someone's role on Manage Users, by Promote / Demote or in
 * Edit (both go through here, so they behave the same).
 *
 * - **Raising** a role needs an [GrantArea] (statewide or nationwide, like the
 *   request flows) and grants it: CREATOR → creator access there; ADMIN →
 *   admin access there plus the mirroring creator access (access is additive).
 *   An existing grant for the same region is switched back on, not duplicated.
 * - **Lowering** a role switches off the grants of the levels left behind:
 *   ADMIN grants below ADMIN, CREATOR grants below CREATOR. Grants are never
 *   rewritten from one role to another.
 */
@Component
class RoleChanges(
    private val users: UserRepository,
    private val roleAssignments: RoleAssignmentRepository,
    private val states: StateRepository,
    private val adminCreatorGrants: AdminCreatorGrants,
    private val roleAuthCache: RoleAuthCache
) {
    fun change(user: User, target: AccessLevel, area: GrantArea?): User {
        val from = user.access
        if (target == from) return user
        // Validate the area before changing anything.
        val regions = if (target > from) regionsOf(area ?: throw bad("Choose the area for the new access")) else emptyList()
        val updated = users.save(user.copy(access = target))
        if (target > from) {
            if (target >= AccessLevel.ADMIN) {
                val admin = upsert(updated, AccessLevel.ADMIN, regions)
                adminCreatorGrants.mirror(updated, admin)
            } else {
                upsert(updated, AccessLevel.CREATOR, regions)
            }
        } else {
            if (target < AccessLevel.ADMIN) disableRole(user.id, AccessLevel.ADMIN)
            if (target < AccessLevel.CREATOR) disableRole(user.id, AccessLevel.CREATOR)
        }
        roleAuthCache.invalidateAuthorizations()
        return updated
    }

    /** States (null = nationwide) for an area; 400 for county/zip or an empty or unknown selection. */
    private fun regionsOf(area: GrantArea): List<State?> {
        requireGrantLevel(area.scopeLevel)
        if (area.scopeLevel == ScopeLevel.NATIONAL) return listOf(null)
        val ids = area.regionIds.distinct()
        val found = states.findAllById(ids).toList()
        if (ids.isEmpty() || found.size != ids.size) throw bad("Unknown or empty state selection")
        return found
    }

    /** Enable [role] grants (every poll type) for [regions], reusing existing rows for the same region. */
    private fun upsert(user: User, role: AccessLevel, regions: List<State?>): List<RoleAssignment> {
        val existing = roleAssignments.findByUserIdAndRole(user.id, role)
        return roleAssignments.saveAll(regions.map { st ->
            val level = if (st == null) ScopeLevel.NATIONAL else ScopeLevel.STATE
            val match = existing.firstOrNull {
                it.scopeLevel == level && it.state?.id == st?.id && it.pollType == null
            }
            match?.copy(enabled = true, disabledReason = null, disabledBy = null, disabledAt = null)
                ?: RoleAssignment(user = user, role = role, scopeLevel = level, state = st, enabled = true)
        })
    }

    private fun disableRole(userId: Long, role: AccessLevel) {
        val rows = roleAssignments.findByUserIdAndRole(userId, role).filter { it.enabled }
        if (rows.isNotEmpty()) roleAssignments.saveAll(rows.map { it.copy(enabled = false) })
    }

    private fun bad(msg: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, msg)
}
