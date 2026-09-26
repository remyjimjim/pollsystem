package org.kodewerks.pollsystem.creatorrequest

import org.kodewerks.pollsystem.authz.RoleAuthCache
import org.kodewerks.pollsystem.email.EmailService
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.CreatorRequest
import org.kodewerks.pollsystem.model.RequestStatus
import org.kodewerks.pollsystem.model.RoleAssignment
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.CreatorRequestRepository
import org.kodewerks.pollsystem.repository.PollTypeRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.kodewerks.pollsystem.repository.StateRepository
import org.kodewerks.pollsystem.payment.PaymentProvider
import org.kodewerks.pollsystem.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class CreatorRequestService(
    private val creatorRequests: CreatorRequestRepository,
    private val roleAssignments: RoleAssignmentRepository,
    private val users: UserRepository,
    private val pollTypes: PollTypeRepository,
    private val countyZips: CountyZipsRepository,
    private val states: StateRepository,
    private val counties: CountyRepository,
    private val email: EmailService,
    private val roleAuthCache: RoleAuthCache,
    private val billing: PaymentProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun submit(user: User, dto: SubmitCreatorRequest): CreatorRequest {
        val pollTypeList = pollTypes.findAllById(dto.pollTypeIds).toList()
        if (pollTypeList.size != dto.pollTypeIds.distinct().size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type")
        }

        val saved = creatorRequests.save(
            CreatorRequest(user = user, reason = dto.reason, status = RequestStatus.PENDING)
        )

        // Fan out one disabled grant row per (region × pollType) at the chosen
        // scope. A coarse scope stores a handful of rows instead of one per zip
        // (NATIONAL = one row per poll type).
        val rows: List<RoleAssignment> = when (dto.scopeLevel) {
            ScopeLevel.NATIONAL -> pollTypeList.map { pt ->
                RoleAssignment(
                    user = user, role = AccessLevel.CREATOR, scopeLevel = ScopeLevel.NATIONAL,
                    pollType = pt, enabled = false, creatorRequest = saved
                )
            }
            ScopeLevel.STATE -> {
                val ids = dto.regionIds.distinct()
                val statesList = states.findAllById(ids).toList()
                if (ids.isEmpty() || statesList.size != ids.size) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or empty state selection")
                }
                statesList.flatMap { st ->
                    pollTypeList.map { pt ->
                        RoleAssignment(
                            user = user, role = AccessLevel.CREATOR, scopeLevel = ScopeLevel.STATE,
                            pollType = pt, state = st, enabled = false, creatorRequest = saved
                        )
                    }
                }
            }
            ScopeLevel.COUNTY -> {
                val ids = dto.regionIds.distinct()
                val countiesList = counties.findAllById(ids).toList()
                if (ids.isEmpty() || countiesList.size != ids.size) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or empty county selection")
                }
                countiesList.flatMap { c ->
                    pollTypeList.map { pt ->
                        RoleAssignment(
                            user = user, role = AccessLevel.CREATOR, scopeLevel = ScopeLevel.COUNTY,
                            pollType = pt, state = c.state, county = c, enabled = false, creatorRequest = saved
                        )
                    }
                }
            }
            ScopeLevel.ZIP -> {
                val zips = dto.zipcodes.distinct()
                val zipToCounty = countyZips.findByZipcodeIn(zips).associateBy { it.zipcode }
                val unknown = zips.filterNot { it in zipToCounty }
                if (zips.isEmpty() || unknown.isNotEmpty()) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or empty zipcodes: $unknown")
                }
                zips.flatMap { zip ->
                    val cz = zipToCounty.getValue(zip)
                    pollTypeList.map { pt ->
                        RoleAssignment(
                            user = user, role = AccessLevel.CREATOR, scopeLevel = ScopeLevel.ZIP,
                            pollType = pt, state = cz.county.state, county = cz.county, zipcode = zip,
                            enabled = false, creatorRequest = saved
                        )
                    }
                }
            }
        }
        roleAssignments.saveAll(rows)
        roleAuthCache.invalidateAuthorizations()

        val assigned = routeToAdmin(rows)
        val withAdmin = if (assigned != null) {
            creatorRequests.save(saved.copy(assignedAdmin = assigned))
        } else {
            log.warn("No covering admin for creator request {} scope={}", saved.id, dto.scopeLevel)
            saved
        }

        val label = regionLabel(rows)
        email.send(
            to = user.email,
            subject = "Your creator request was received",
            body = "Your request to become a Creator for $label is being processed. " +
                "We will notify you once it is reviewed."
        )
        if (assigned != null) {
            email.send(
                to = assigned.email,
                subject = "New Creator Request awaiting review",
                body = "User ${user.email} requested Creator access for: $label\n" +
                    "Reason: ${dto.reason}"
            )
        }

        return withAdmin
    }

    private fun routeToAdmin(rows: List<RoleAssignment>): User? {
        // National requests have no single covering admin — leave unassigned so
        // any admin can pick them up from the dashboard.
        if (scopeOf(rows) == ScopeLevel.NATIONAL) return null
        val reqStates = rows.mapNotNull { it.state?.id }.toSet()
        if (reqStates.isEmpty()) return null
        // Admins whose enabled ADMIN grants cover any of the request's states
        // (admins are still ZIP-granted, so their state set comes off those rows).
        val statesByAdmin = roleAssignments.findByRoleAndEnabled(AccessLevel.ADMIN, true)
            .groupBy { it.user.id }
            .mapValues { (_, rs) -> rs.mapNotNull { it.state?.id }.toSet() }
        val candidateIds = statesByAdmin.filterValues { sts -> sts.any { it in reqStates } }.keys
        if (candidateIds.isEmpty()) return null
        val candidates = users.findAllById(candidateIds).filter { it.isEnabled }
        if (candidates.isEmpty()) return null
        val winner = candidates.minBy { roleAuthCache.pendingCount(it) }
        roleAuthCache.bumpPendingCount(winner)
        return winner
    }

    @Transactional
    fun listForUser(userId: Long): List<CreatorRequest> =
        creatorRequests.findByUserId(userId)

    @Transactional
    fun listForAdmin(admin: User): List<CreatorRequest> {
        val mine = creatorRequests.findByAssignedAdminAndStatus(admin, RequestStatus.PENDING)
        // Stale (unassigned) PENDING requests are claimable by any admin
        val stale = creatorRequests.findByStatus(RequestStatus.PENDING)
            .filter { it.assignedAdmin == null }
        return (mine + stale).distinctBy { it.id }.sortedBy { it.submittedAt }
    }

    @Transactional
    fun batchApprove(requestIds: List<Long>, decidedBy: User): List<CreatorRequest> =
        decide(requestIds, RequestStatus.APPROVED, decidedBy)

    @Transactional
    fun batchReject(requestIds: List<Long>, decidedBy: User): List<CreatorRequest> =
        decide(requestIds, RequestStatus.REJECTED, decidedBy)

    private fun decide(
        requestIds: List<Long>,
        decision: RequestStatus,
        decidedBy: User
    ): List<CreatorRequest> {
        val now = Instant.now()
        // Skip rows already in the target status (no-op). Allows flipping
        // PENDING↔APPROVED↔REJECTED so admins can revise a decision later.
        val targets = creatorRequests.findAllById(requestIds.distinct())
            .filter { it.status != decision }
        if (targets.isEmpty()) return emptyList()

        val rowsByRequest = roleAssignments
            .findByCreatorRequestIdIn(targets.map { it.id })
            .groupBy { it.creatorRequest!!.id }

        val results = mutableListOf<CreatorRequest>()
        for (req in targets) {
            val previous = req.status
            val updated = creatorRequests.save(
                req.copy(status = decision, processedAt = now, processedBy = decidedBy)
            )
            val rows = rowsByRequest[req.id].orEmpty()
            if (decision == RequestStatus.APPROVED) {
                roleAssignments.saveAll(rows.map { it.copy(enabled = true) })
                if (req.user.access.ordinal < AccessLevel.CREATOR.ordinal) {
                    users.save(req.user.copy(access = AccessLevel.CREATOR))
                    // Reward the new creator with the discounted subscription rate.
                    billing.applyCreatorDiscount(req.user)
                }
                email.send(
                    to = req.user.email,
                    subject = "You are now a Creator!",
                    body = "Your creator request was approved. Visit /home to start creating polls."
                )
            } else {
                // On flip-from-APPROVED, disable the rows that approval enabled.
                // On fresh PENDING→REJECTED the rows are already disabled — no-op.
                // We deliberately do NOT downgrade user.access: they may hold
                // enabled CREATOR rows from other approved requests.
                if (previous == RequestStatus.APPROVED) {
                    roleAssignments.saveAll(rows.map { it.copy(enabled = false) })
                }
                email.send(
                    to = req.user.email,
                    subject = "Your creator request was not approved",
                    body = "Your creator request was reviewed and not approved at this time."
                )
            }
            results += updated
        }
        // Any flip in this batch toggled role_assignments.enabled, so the
        // cached "who's authorized" sets are stale. Nuke once per batch
        // rather than per row — eviction is cheap, re-fetch cold-starts
        // in the next handful of routing calls.
        roleAuthCache.invalidateAuthorizations()
        return results
    }

    /**
     * Mark stale assignments as claimable by clearing assigned_admin_id.
     * Per the UML stale-request-fallback path.
     */
    @Transactional
    fun unassignStale(threshold: Instant): Int {
        val stale = creatorRequests.findStaleRequests(threshold)
            .filter { it.assignedAdmin != null }
        stale.forEach { creatorRequests.save(it.copy(assignedAdmin = null)) }
        if (stale.isNotEmpty()) {
            log.info("Unassigned {} stale creator requests", stale.size)
        }
        return stale.size
    }

    fun toDto(req: CreatorRequest): CreatorRequestDto {
        val rows = roleAssignments.findByCreatorRequestId(req.id)
        val scope = scopeOf(rows)
        val zips = if (scope == ScopeLevel.ZIP) rows.mapNotNull { it.zipcode }.distinct().sorted() else emptyList()
        val pollTypeIds = rows.mapNotNull { it.pollType?.id }.distinct().sorted()
        return CreatorRequestDto.from(req, scope, stateIdsOf(rows), regionLabel(rows), zips, pollTypeIds)
    }

    private fun scopeOf(rows: List<RoleAssignment>): ScopeLevel =
        rows.firstOrNull()?.scopeLevel ?: ScopeLevel.ZIP

    private fun stateIdsOf(rows: List<RoleAssignment>): List<Long> =
        rows.mapNotNull { it.state?.id }.distinct().sorted()

    /** Human-readable purview for emails and the admin queue. */
    private fun regionLabel(rows: List<RoleAssignment>): String = when (scopeOf(rows)) {
        ScopeLevel.NATIONAL -> "National"
        ScopeLevel.STATE -> rows.mapNotNull { it.state?.name }.distinct().sorted().joinToString(", ")
        ScopeLevel.COUNTY ->
            rows.mapNotNull { r -> r.county?.let { "${it.name} (${it.state.initial})" } }
                .distinct().sorted().joinToString(", ")
        ScopeLevel.ZIP -> {
            val zips = rows.mapNotNull { it.zipcode }.distinct().sorted()
            if (zips.size <= 8) zips.joinToString(", ") else "${zips.size} zipcodes"
        }
    }
}
