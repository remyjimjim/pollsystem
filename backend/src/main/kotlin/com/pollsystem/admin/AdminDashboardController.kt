package com.pollsystem.admin

import com.pollsystem.creatorrequest.CreatorRequestDto
import com.pollsystem.creatorrequest.CreatorRequestService
import com.pollsystem.model.AccessLevel
import com.pollsystem.model.RequestStatus
import com.pollsystem.repository.CreatorRequestRepository
import com.pollsystem.repository.RoleAssignmentRepository
import com.pollsystem.security.AppUserDetails
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.temporal.ChronoUnit

data class AdminZipcodeScope(
    val stateInitial: String,
    val countyName: String,
    val zipcode: String
)

data class RecentDecisionDto(
    val requestId: Long,
    val userEmail: String,
    val zipcodes: List<String>,
    val status: RequestStatus,
    val processedAt: Instant?
)

data class AdminDashboardDto(
    val scope: List<AdminZipcodeScope>,
    val pendingAssignedToMe: List<CreatorRequestDto>,
    val staleInScope: List<CreatorRequestDto>,
    val creatorsInScopeCount: Int,
    val recentDecisions: List<RecentDecisionDto>
)

@RestController
@RequestMapping("/api/admin")
class AdminDashboardController(
    private val service: CreatorRequestService,
    private val creatorRequests: CreatorRequestRepository,
    private val roleAssignments: RoleAssignmentRepository
) {
    private val staleThresholdHours = 48L

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    fun dashboard(@AuthenticationPrincipal principal: AppUserDetails): AdminDashboardDto {
        val me = principal.user

        // Zipcodes I administer
        val myAdminRows = roleAssignments
            .findByUserIdAndRole(me.id, AccessLevel.ADMIN)
            .filter { it.enabled }
        val scope = myAdminRows.map {
            AdminZipcodeScope(
                stateInitial = it.state.initial,
                countyName = it.county.name,
                zipcode = it.zipcode
            )
        }.sortedWith(compareBy({ it.stateInitial }, { it.countyName }, { it.zipcode }))
        val myZipcodes = scope.map { it.zipcode }.toSet()

        // Pending requests routed to me
        val mine = creatorRequests.findByAssignedAdminAndStatus(me, RequestStatus.PENDING)
        val pending = mine.sortedByDescending { it.submittedAt }.map(service::toDto)

        // Stale unassigned requests anywhere in my scope
        val staleCutoff = Instant.now().minus(staleThresholdHours, ChronoUnit.HOURS)
        val staleRequests = creatorRequests.findStaleRequests(staleCutoff)
            .filter { it.assignedAdmin == null }
            .map(service::toDto)
            .filter { dto -> dto.zipcodes.any { it in myZipcodes } }

        // Creators with enabled CREATOR role assignments in my scope
        val creatorsInScopeCount = if (myZipcodes.isEmpty()) {
            0
        } else {
            roleAssignments
                .findEnabledByRoleAndZipcodes(AccessLevel.CREATOR, myZipcodes.toList())
                .map { it.user.id }
                .toSet()
                .size
        }

        // Recent decisions by me (most recent 10)
        val recent = creatorRequests.findRecentDecisionsBy(me, PageRequest.of(0, 10))
            .map { req ->
                val zipcodes = service.toDto(req).zipcodes
                RecentDecisionDto(
                    requestId = req.id,
                    userEmail = req.user.email,
                    zipcodes = zipcodes,
                    status = req.status,
                    processedAt = req.processedAt
                )
            }

        return AdminDashboardDto(
            scope = scope,
            pendingAssignedToMe = pending,
            staleInScope = staleRequests,
            creatorsInScopeCount = creatorsInScopeCount,
            recentDecisions = recent
        )
    }
}
