package org.kodewerks.pollsystem.admin

import org.kodewerks.pollsystem.creatorrequest.CreatorRequestDto
import org.kodewerks.pollsystem.creatorrequest.CreatorRequestService
import org.kodewerks.pollsystem.geography.RegionService
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.RequestStatus
import org.kodewerks.pollsystem.model.RoleAssignment
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.CreatorRequestRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.temporal.ChronoUnit

data class RecentDecisionDto(
    val requestId: Long,
    val userEmail: String,
    /** The request's area, e.g. "California" or "Nationwide". */
    val regionLabel: String,
    val status: RequestStatus,
    val processedAt: Instant?
)

data class AdminDashboardDto(
    /** The caller's admin area, one label per enabled ADMIN grant: "Nationwide", "California", "Los Angeles (CA)", "90001". */
    val areas: List<String>,
    val pendingAssignedToMe: List<CreatorRequestDto>,
    /** Unassigned PENDING requests in my scope, claimable by any admin in scope. */
    val unassignedInScope: List<CreatorRequestDto>,
    /** Count of unassignedInScope rows older than the stale threshold (48h). */
    val staleCount: Int,
    val creatorsInScopeCount: Int,
    val recentDecisions: List<RecentDecisionDto>
)

@RestController
@RequestMapping("/api/admin")
class AdminDashboardController(
    private val service: CreatorRequestService,
    private val creatorRequests: CreatorRequestRepository,
    private val roleAssignments: RoleAssignmentRepository,
    private val regions: RegionService
) {
    private val staleThresholdHours = 48L

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    fun dashboard(@AuthenticationPrincipal principal: AppUserDetails): AdminDashboardDto {
        val me = principal.user

        // My admin area, at any level (admins are granted statewide or
        // nationwide now; older county/zip grants still count).
        val myAdminRows = roleAssignments
            .findByUserIdAndRole(me.id, AccessLevel.ADMIN)
            .filter { it.enabled }
        val areas = myAdminRows
            .sortedWith(compareBy({ it.scopeLevel.ordinal }, { it.state?.initial }, { it.county?.name }, { it.zipcode }))
            .map { areaLabel(it) }
            .distinct()
        val nationwide = myAdminRows.any { it.scopeLevel == ScopeLevel.NATIONAL }
        // States I administer — for scope-aware creator-request visibility below.
        val myStateIds = myAdminRows.mapNotNull { it.state?.id }.toSet()

        // Pending requests routed to me
        val mine = creatorRequests.findByAssignedAdminAndStatus(me, RequestStatus.PENDING)
        val pending = mine.sortedByDescending { it.submittedAt }.map(service::toDto)

        // All unassigned PENDING requests intersecting my scope — claimable by
        // any admin in scope, regardless of age. (Routing returns null when no
        // admin covered the zipcode at submit time; once a scope-matching admin
        // exists, they should see those requests immediately.) Stale rows are
        // a per-row visual badge driven off submittedAt, not a separate bucket.
        val staleCutoff = Instant.now().minus(staleThresholdHours, ChronoUnit.HOURS)
        val unassigned = creatorRequests.findByStatus(RequestStatus.PENDING)
            .filter { it.assignedAdmin == null }
            .map(service::toDto)
            // Visible if the request's scope intersects my states (all, if I'm a
            // nationwide admin); NATIONAL requests to every admin.
            .filter { dto -> nationwide || dto.scopeLevel == ScopeLevel.NATIONAL || dto.stateIds.any { it in myStateIds } }
            .sortedByDescending { it.submittedAt }
        val staleCount = unassigned.count { it.submittedAt.isBefore(staleCutoff) }

        // Creators with an enabled CREATOR grant overlapping my area (admins
        // included: they hold creator grants mirroring their admin area).
        val myRegions = regions.adminRegions(me)
        val creatorsInScopeCount = if (myRegions.isEmpty()) 0 else roleAssignments.findByRole(AccessLevel.CREATOR)
            .filter { g -> g.enabled && regions.ofGrant(g)?.let { r -> myRegions.any { it.overlaps(r) } } == true }
            .map { it.user.id }
            .toSet()
            .size

        // Recent decisions by me (most recent 10)
        val recent = creatorRequests.findRecentDecisionsBy(me, PageRequest.of(0, 10))
            .map { req ->
                RecentDecisionDto(
                    requestId = req.id,
                    userEmail = req.user.email,
                    regionLabel = service.toDto(req).regionLabel,
                    status = req.status,
                    processedAt = req.processedAt
                )
            }

        return AdminDashboardDto(
            areas = areas,
            pendingAssignedToMe = pending,
            unassignedInScope = unassigned,
            staleCount = staleCount,
            creatorsInScopeCount = creatorsInScopeCount,
            recentDecisions = recent
        )
    }
}

private fun areaLabel(g: RoleAssignment): String = when (g.scopeLevel) {
    ScopeLevel.NATIONAL -> "Nationwide"
    ScopeLevel.STATE -> g.state?.name ?: "state"
    ScopeLevel.COUNTY -> "${g.county?.name} (${g.state?.initial ?: g.county?.state?.initial})"
    ScopeLevel.ZIP -> g.zipcode ?: "zipcode"
}
