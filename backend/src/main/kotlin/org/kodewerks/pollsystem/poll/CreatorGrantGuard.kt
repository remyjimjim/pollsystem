package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollPurview
import org.kodewerks.pollsystem.model.PollType
import org.kodewerks.pollsystem.model.RoleAssignment
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.kodewerks.pollsystem.repository.StateRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Enforces a creator's grants (enabled `role_assignments`) on the polls they
 * write: every region of a poll's purview must be covered by an enabled grant
 * for the poll's type. Without this, `requireCreator` only checked the access
 * level, so disabling or narrowing a creator's grants restricted nothing.
 *
 * Coverage is by scope level, like purview membership: a NATIONAL grant covers
 * everything; a STATE grant covers that state and its counties/zips; a COUNTY
 * grant that county and its zips; a ZIP grant only that zip. A poll purview of
 * NATIONAL (or no rows) needs a NATIONAL grant. A grant with no poll type
 * covers every type. ADMINs' own enabled ADMIN grants count too (an admin may
 * create within their purview); SUPER is unrestricted.
 *
 * Callers invoke this after writing the purview inside their transaction, so a
 * rejection (403) rolls the whole save back.
 */
@Component
class CreatorGrantGuard(
    private val roleAssignments: RoleAssignmentRepository,
    private val countyZips: CountyZipsRepository,
    private val counties: CountyRepository,
    private val states: StateRepository
) {

    @Transactional(readOnly = true)
    fun requireCovers(user: User, pollType: PollType, purviewRows: List<PollPurview>) {
        val missing = uncovered(user, pollType, purviewRows)
        if (missing.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Your creator access doesn't cover ${missing.joinToString(", ")} for ${pollType.name} polls"
            )
        }
    }

    /** Human-readable labels of the purview regions [user]'s grants don't cover (empty = allowed). */
    @Transactional(readOnly = true)
    fun uncovered(user: User, pollType: PollType, purviewRows: List<PollPurview>): List<String> {
        if (user.access >= AccessLevel.SUPER) return emptyList()
        val grants = roleAssignments.findByUserId(user.id).filter { usable(it, user, pollType) }
        if (grants.any { it.scopeLevel == ScopeLevel.NATIONAL }) return emptyList()

        val stateGrants = grants.filter { it.scopeLevel == ScopeLevel.STATE }.mapNotNull { it.state?.id }.toSet()
        val countyGrants = grants.filter { it.scopeLevel == ScopeLevel.COUNTY }.mapNotNull { it.county?.id }.toSet()
        val zipGrants = grants.filter { it.scopeLevel == ScopeLevel.ZIP }.mapNotNull { it.zipcode }.toSet()

        val rows = purviewRows.ifEmpty { listOf(NATIONWIDE) }
        val stateById = states.findAllById(rows.mapNotNull { it.stateId }.distinct()).associateBy { it.id }
        val countyById = counties.findAllById(rows.mapNotNull { it.countyId }.distinct()).associateBy { it.id }
        val zipMeta = rows.mapNotNull { it.zipcode }.distinct().let { zips ->
            if (zips.isEmpty()) emptyMap() else countyZips.findByZipcodeIn(zips).groupBy { it.zipcode }
        }

        return rows.mapNotNull { r ->
            when (r.scopeLevel) {
                ScopeLevel.NATIONAL -> "Nationwide"
                ScopeLevel.STATE -> if (r.stateId in stateGrants) null else stateById[r.stateId]?.name ?: "state #${r.stateId}"
                ScopeLevel.COUNTY -> {
                    val c = countyById[r.countyId]
                    if (r.countyId in countyGrants || c?.state?.id in stateGrants) null
                    else c?.let { "${it.name} (${it.state.initial})" } ?: "county #${r.countyId}"
                }
                ScopeLevel.ZIP -> {
                    val metas = zipMeta[r.zipcode].orEmpty()
                    if (r.zipcode in zipGrants ||
                        metas.any { it.county.id in countyGrants || it.county.state.id in stateGrants }
                    ) null else r.zipcode
                }
            }
        }.distinct()
    }

    private fun usable(g: RoleAssignment, user: User, pollType: PollType): Boolean =
        g.enabled &&
            (g.role == AccessLevel.CREATOR || (g.role == AccessLevel.ADMIN && user.access >= AccessLevel.ADMIN)) &&
            (g.pollType == null || g.pollType.id == pollType.id)

    private companion object {
        val NATIONWIDE = PollPurview(
            pollType = PollKind.QUESTIONNAIRE,
            pollId = 0, scopeLevel = ScopeLevel.NATIONAL
        )
    }
}
