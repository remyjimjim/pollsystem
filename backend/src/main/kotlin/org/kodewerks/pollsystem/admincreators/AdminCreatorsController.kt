package org.kodewerks.pollsystem.admincreators

import org.kodewerks.pollsystem.authz.RoleAuthCache
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.RequestStatus
import org.kodewerks.pollsystem.model.RoleAssignment
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.PollTypeRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.kodewerks.pollsystem.repository.StateRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/** One creator grant as shown on /admin/manage-creators. */
data class GrantDto(
    val id: Long,
    val scopeLevel: ScopeLevel,
    val stateId: Long?,
    val stateName: String?,
    val stateInitial: String?,
    val countyId: Long?,
    val countyName: String?,
    val zipcode: String?,
    /** null = every poll type. */
    val pollTypeId: Long?,
    val pollTypeName: String?,
    val enabled: Boolean,
    /** Inside the caller's purview, so they may toggle / remove it. */
    val manageable: Boolean,
    /** Came from an approved creator request (kept for its history: disable, don't remove). */
    val fromRequest: Boolean
)

enum class EnabledState { ENABLED, DISABLED, PARTIAL }

data class CreatorRow(
    val userId: Long,
    val email: String,
    /** Over the grants the caller can manage (or all visible ones if none are manageable). */
    val enabledState: EnabledState,
    val manageable: Boolean,
    val grants: List<GrantDto>,
    val pollCount: Long,
    val lastEditedAt: Instant?
)

data class SetEnabledRequest(val enabled: Boolean)

/** Grant [scopeLevel] regions to a creator; empty [pollTypeIds] = every poll type. */
data class AddGrantsRequest(
    val scopeLevel: ScopeLevel,
    val regionIds: List<Long> = emptyList(),
    val zipcodes: List<String> = emptyList(),
    val pollTypeIds: List<Long> = emptyList()
)

/**
 * Backs /admin/manage-creators. A creator's purview is their CREATOR grants
 * (role_assignments), which CreatorGrantGuard enforces on every poll write.
 *
 * Only grants from APPROVED creator requests (or added here, with no request)
 * count: pending/rejected requests also store disabled rows, which must not
 * read as "disabled creator". An ADMIN sees creators with a grant overlapping
 * their own purview and may toggle / add / remove only the grants fully inside
 * it; SUPER sees and manages everything.
 */
@RestController
@RequestMapping("/api/admin/creators")
class AdminCreatorsController(
    private val roleAssignments: RoleAssignmentRepository,
    private val users: UserRepository,
    private val states: StateRepository,
    private val counties: CountyRepository,
    private val countyZips: CountyZipsRepository,
    private val pollTypes: PollTypeRepository,
    private val questionnaires: QuestionnaireRepository,
    private val elections: ElectionRepository,
    private val ballotMeasures: BallotMeasureRepository,
    private val roleAuthCache: RoleAuthCache
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun list(@AuthenticationPrincipal principal: AppUserDetails): List<CreatorRow> {
        val reach = reachOf(principal.user)
        val byUser = roleAssignments.findByRole(AccessLevel.CREATOR)
            .filter { counted(it) && reach.overlaps(it) }
            .groupBy { it.user.id }
        if (byUser.isEmpty()) return emptyList()
        val stats = editStats(byUser.keys.toList())
        return byUser.values
            .map { grants -> toRow(grants.first().user, grants, reach, stats[grants.first().user.id]) }
            .sortedBy { it.email.lowercase() }
    }

    /** Enable / disable every grant of this creator that the caller can manage. */
    @PutMapping("/{userId}/enabled")
    @Transactional
    fun setEnabled(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable userId: Long,
        @RequestBody body: SetEnabledRequest
    ): CreatorRow {
        val reach = reachOf(principal.user)
        val mine = roleAssignments.findByUserIdAndRole(userId, AccessLevel.CREATOR)
            .filter { counted(it) && reach.contains(it) }
        if (mine.isEmpty()) throw forbidden("None of this creator's grants are inside your purview")
        roleAssignments.saveAll(mine.filter { it.enabled != body.enabled }.map { it.copy(enabled = body.enabled) })
        roleAuthCache.invalidateAuthorizations()
        return rowFor(userId, reach)
    }

    @PutMapping("/{userId}/grants/{grantId}")
    @Transactional
    fun setGrantEnabled(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable userId: Long,
        @PathVariable grantId: Long,
        @RequestBody body: SetEnabledRequest
    ): CreatorRow {
        val reach = reachOf(principal.user)
        val g = manageableGrant(userId, grantId, reach)
        if (g.enabled != body.enabled) roleAssignments.save(g.copy(enabled = body.enabled))
        roleAuthCache.invalidateAuthorizations()
        return rowFor(userId, reach)
    }

    /**
     * Grants removed here are ones an admin added; grants from a creator request
     * are kept for that request's history and can only be disabled.
     */
    @DeleteMapping("/{userId}/grants/{grantId}")
    @Transactional
    fun removeGrant(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable userId: Long,
        @PathVariable grantId: Long
    ): CreatorRow {
        val reach = reachOf(principal.user)
        val g = manageableGrant(userId, grantId, reach)
        if (g.creatorRequest != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This grant came from a creator request; disable it instead")
        }
        roleAssignments.delete(g)
        roleAuthCache.invalidateAuthorizations()
        return rowFor(userId, reach)
    }

    /** Add (enabled) grants for regions inside the caller's purview; re-enables matching existing ones. */
    @PostMapping("/{userId}/grants")
    @Transactional
    fun addGrants(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable userId: Long,
        @RequestBody body: AddGrantsRequest
    ): CreatorRow {
        val reach = reachOf(principal.user)
        val user = users.findById(userId).orElseThrow { notFound("User not found") }
        if (user.access < AccessLevel.CREATOR) throw bad("${user.email} is not a creator")

        val types = body.pollTypeIds.distinct().let { ids ->
            if (ids.isEmpty()) listOf(null)
            else pollTypes.findAllById(ids).toList().also { if (it.size != ids.size) throw bad("Unknown poll type") }
        }
        val candidates = regionGrants(user, body).flatMap { base -> types.map { base.copy(pollType = it) } }
        candidates.firstOrNull { !reach.contains(it) }?.let { throw forbidden("${label(it)} is outside your purview") }

        val existing = roleAssignments.findByUserIdAndRole(userId, AccessLevel.CREATOR).filter { counted(it) }
        val toSave = candidates.mapNotNull { c ->
            val match = existing.firstOrNull { sameRegionAndType(it, c) }
            when {
                match == null -> c
                !match.enabled -> match.copy(enabled = true)
                else -> null
            }
        }
        roleAssignments.saveAll(toSave)
        roleAuthCache.invalidateAuthorizations()
        return rowFor(userId, reach)
    }

    // ---------- helpers ----------

    /** Grants that represent real access: approved, or added directly (no request). */
    private fun counted(g: RoleAssignment) =
        g.creatorRequest == null || g.creatorRequest.status == RequestStatus.APPROVED

    private fun manageableGrant(userId: Long, grantId: Long, reach: Reach): RoleAssignment {
        val g = roleAssignments.findById(grantId).orElseThrow { notFound("Grant not found") }
        if (g.user.id != userId || g.role != AccessLevel.CREATOR || !counted(g)) throw notFound("Grant not found")
        if (!reach.contains(g)) throw forbidden("${label(g)} is outside your purview")
        return g
    }

    private fun rowFor(userId: Long, reach: Reach): CreatorRow {
        val user = users.findById(userId).orElseThrow { notFound("User not found") }
        val grants = roleAssignments.findByUserIdAndRole(userId, AccessLevel.CREATOR)
            .filter { counted(it) && reach.overlaps(it) }
        return toRow(user, grants, reach, editStats(listOf(userId))[userId])
    }

    private fun toRow(user: User, grants: List<RoleAssignment>, reach: Reach, stats: Pair<Long, Instant?>?): CreatorRow {
        val manageable = grants.filter { reach.contains(it) }
        val basis = manageable.ifEmpty { grants }
        val state = when {
            basis.all { it.enabled } -> EnabledState.ENABLED
            basis.none { it.enabled } -> EnabledState.DISABLED
            else -> EnabledState.PARTIAL
        }
        return CreatorRow(
            userId = user.id,
            email = user.email,
            enabledState = state,
            manageable = manageable.isNotEmpty(),
            grants = grants.sortedWith(compareBy({ it.scopeLevel.ordinal }, { label(it) })).map { toDto(it, reach) },
            pollCount = stats?.first ?: 0,
            lastEditedAt = stats?.second
        )
    }

    private fun toDto(g: RoleAssignment, reach: Reach) = GrantDto(
        id = g.id, scopeLevel = g.scopeLevel,
        stateId = g.state?.id, stateName = g.state?.name, stateInitial = g.state?.initial,
        countyId = g.county?.id, countyName = g.county?.name, zipcode = g.zipcode,
        pollTypeId = g.pollType?.id, pollTypeName = g.pollType?.name,
        enabled = g.enabled, manageable = reach.contains(g), fromRequest = g.creatorRequest != null
    )

    /** userId → (poll count across all kinds, latest creator_edited_at). */
    private fun editStats(userIds: List<Long>): Map<Long, Pair<Long, Instant?>> {
        val acc = mutableMapOf<Long, Pair<Long, Instant?>>()
        val rows = questionnaires.editStatsByCreatorIds(userIds) +
            elections.editStatsByCreatorIds(userIds) +
            ballotMeasures.editStatsByCreatorIds(userIds)
        for (r in rows) {
            val id = r[0] as Long
            val count = (r[1] as Number).toLong()
            val last = r[2] as Instant?
            val (c0, l0) = acc[id] ?: (0L to null)
            acc[id] = (c0 + count) to listOfNotNull(l0, last).maxOrNull()
        }
        return acc
    }

    /** Unsaved grant rows (no poll type yet) for the requested regions. */
    private fun regionGrants(user: User, body: AddGrantsRequest): List<RoleAssignment> {
        fun grant(level: ScopeLevel) = RoleAssignment(user = user, role = AccessLevel.CREATOR, scopeLevel = level, enabled = true)
        return when (body.scopeLevel) {
            ScopeLevel.NATIONAL -> listOf(grant(ScopeLevel.NATIONAL))
            ScopeLevel.STATE -> {
                val ids = body.regionIds.distinct()
                val found = states.findAllById(ids).toList()
                if (ids.isEmpty() || found.size != ids.size) throw bad("Unknown or empty state selection")
                found.map { grant(ScopeLevel.STATE).copy(state = it) }
            }
            ScopeLevel.COUNTY -> {
                val ids = body.regionIds.distinct()
                val found = counties.findAllById(ids).toList()
                if (ids.isEmpty() || found.size != ids.size) throw bad("Unknown or empty county selection")
                found.map { grant(ScopeLevel.COUNTY).copy(state = it.state, county = it) }
            }
            ScopeLevel.ZIP -> {
                val zips = body.zipcodes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                val meta = countyZips.findByZipcodeIn(zips).associateBy { it.zipcode }
                val unknown = zips.filterNot { it in meta }
                if (zips.isEmpty() || unknown.isNotEmpty()) throw bad("Unknown or empty zipcodes: $unknown")
                zips.map { z ->
                    val c = meta.getValue(z).county
                    grant(ScopeLevel.ZIP).copy(state = c.state, county = c, zipcode = z)
                }
            }
        }
    }

    private fun sameRegionAndType(a: RoleAssignment, b: RoleAssignment) =
        a.scopeLevel == b.scopeLevel && a.state?.id == b.state?.id && a.county?.id == b.county?.id &&
            a.zipcode == b.zipcode && a.pollType?.id == b.pollType?.id

    private fun label(g: RoleAssignment): String = when (g.scopeLevel) {
        ScopeLevel.NATIONAL -> "Nationwide"
        ScopeLevel.STATE -> g.state?.name ?: "state"
        ScopeLevel.COUNTY -> "${g.county?.name} (${g.state?.initial ?: g.county?.state?.initial})"
        ScopeLevel.ZIP -> g.zipcode ?: "zipcode"
    }

    /**
     * The caller's reach, from their enabled ADMIN grants by scope level (SUPER
     * or a NATIONAL admin grant = unrestricted). [contains]: a grant lies wholly
     * inside it (manageable). [overlaps]: they share any territory (visible).
     */
    private class Reach(
        val unrestricted: Boolean,
        val stateIds: Set<Long> = emptySet(),
        val countyIds: Set<Long> = emptySet(),
        val zipcodes: Set<String> = emptySet(),
        /** States/counties partly covered (e.g. by a county or zip grant) — for overlap only. */
        val touchedStateIds: Set<Long> = emptySet(),
        val touchedCountyIds: Set<Long> = emptySet()
    ) {
        fun contains(g: RoleAssignment): Boolean = unrestricted || when (g.scopeLevel) {
            ScopeLevel.NATIONAL -> false
            ScopeLevel.STATE -> g.state?.id in stateIds
            ScopeLevel.COUNTY -> g.county?.id in countyIds || g.county?.state?.id in stateIds
            ScopeLevel.ZIP -> g.zipcode in zipcodes || g.county?.id in countyIds || g.state?.id in stateIds
        }

        fun overlaps(g: RoleAssignment): Boolean = unrestricted || contains(g) || when (g.scopeLevel) {
            ScopeLevel.NATIONAL -> stateIds.isNotEmpty() || touchedStateIds.isNotEmpty()
            ScopeLevel.STATE -> g.state?.id in touchedStateIds
            ScopeLevel.COUNTY -> g.county?.id in touchedCountyIds
            ScopeLevel.ZIP -> false
        }
    }

    private fun reachOf(user: User): Reach {
        if (user.access >= AccessLevel.SUPER) return Reach(unrestricted = true)
        val mine = roleAssignments.findByUserIdAndRole(user.id, AccessLevel.ADMIN).filter { it.enabled }
        if (mine.any { it.scopeLevel == ScopeLevel.NATIONAL }) return Reach(unrestricted = true)
        val countyGrants = mine.filter { it.scopeLevel == ScopeLevel.COUNTY }
        val zipGrants = mine.filter { it.scopeLevel == ScopeLevel.ZIP }
        return Reach(
            unrestricted = false,
            stateIds = mine.filter { it.scopeLevel == ScopeLevel.STATE }.mapNotNull { it.state?.id }.toSet(),
            countyIds = countyGrants.mapNotNull { it.county?.id }.toSet(),
            zipcodes = zipGrants.mapNotNull { it.zipcode }.toSet(),
            touchedStateIds = (countyGrants + zipGrants).mapNotNull { it.state?.id ?: it.county?.state?.id }.toSet(),
            touchedCountyIds = zipGrants.mapNotNull { it.county?.id }.toSet()
        )
    }

    private fun bad(msg: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, msg)
    private fun forbidden(msg: String) = ResponseStatusException(HttpStatus.FORBIDDEN, msg)
    private fun notFound(msg: String) = ResponseStatusException(HttpStatus.NOT_FOUND, msg)
}
