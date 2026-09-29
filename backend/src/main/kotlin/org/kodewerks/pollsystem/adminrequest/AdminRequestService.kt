package org.kodewerks.pollsystem.adminrequest

import org.kodewerks.pollsystem.authz.RoleAuthCache
import org.kodewerks.pollsystem.email.EmailService
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.AdminRequest
import org.kodewerks.pollsystem.model.RequestStatus
import org.kodewerks.pollsystem.model.RoleAssignment
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.AdminRequestRepository
import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.kodewerks.pollsystem.repository.StateRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class AdminRequestService(
    private val adminRequests: AdminRequestRepository,
    private val roleAssignments: RoleAssignmentRepository,
    private val users: UserRepository,
    private val countyZips: CountyZipsRepository,
    private val states: StateRepository,
    private val counties: CountyRepository,
    private val email: EmailService,
    private val roleAuthCache: RoleAuthCache,
) {

    @Transactional
    fun submit(user: User, dto: SubmitAdminRequest): AdminRequest {
        if (user.access.ordinal < AccessLevel.CREATOR.ordinal) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You must be a Creator before requesting Admin"
            )
        }
        val saved = adminRequests.save(
            AdminRequest(
                user = user,
                reason = dto.reason,
                status = RequestStatus.PENDING
            )
        )
        // Fan out one disabled grant row per region at the chosen scope. Admins
        // moderate all poll types, so (unlike creator requests) there's no
        // per-pollType multiplication — one row per region.
        val rows: List<RoleAssignment> = when (dto.scopeLevel) {
            ScopeLevel.NATIONAL -> listOf(
                RoleAssignment(
                    user = user, role = AccessLevel.ADMIN, scopeLevel = ScopeLevel.NATIONAL,
                    enabled = false, adminRequest = saved
                )
            )
            ScopeLevel.STATE -> {
                val ids = dto.regionIds.distinct()
                val statesList = states.findAllById(ids).toList()
                if (ids.isEmpty() || statesList.size != ids.size) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or empty state selection")
                }
                statesList.map { st ->
                    RoleAssignment(
                        user = user, role = AccessLevel.ADMIN, scopeLevel = ScopeLevel.STATE,
                        state = st, enabled = false, adminRequest = saved
                    )
                }
            }
            ScopeLevel.COUNTY -> {
                val ids = dto.regionIds.distinct()
                val countiesList = counties.findAllById(ids).toList()
                if (ids.isEmpty() || countiesList.size != ids.size) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or empty county selection")
                }
                countiesList.map { c ->
                    RoleAssignment(
                        user = user, role = AccessLevel.ADMIN, scopeLevel = ScopeLevel.COUNTY,
                        state = c.state, county = c, enabled = false, adminRequest = saved
                    )
                }
            }
            ScopeLevel.ZIP -> {
                val zips = dto.zipcodes.distinct()
                val zipToCounty = countyZips.findByZipcodeIn(zips).associateBy { it.zipcode }
                val unknown = zips.filterNot { it in zipToCounty }
                if (zips.isEmpty() || unknown.isNotEmpty()) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or empty zipcodes: $unknown")
                }
                zips.map { zip ->
                    val cz = zipToCounty.getValue(zip)
                    RoleAssignment(
                        user = user, role = AccessLevel.ADMIN, scopeLevel = ScopeLevel.ZIP,
                        state = cz.county.state, county = cz.county, zipcode = zip,
                        enabled = false, adminRequest = saved
                    )
                }
            }
        }
        roleAssignments.saveAll(rows)
        roleAuthCache.invalidateAuthorizations()

        val label = regionLabel(rows)
        email.send(
            to = user.email,
            subject = "Your admin request was received",
            body = "Your request for Admin coverage of $label is being reviewed. " +
                "We will notify you once a Super reviews it."
        )
        users.findByAccess(AccessLevel.SUPER)
            .filter { it.isEnabled }
            .forEach { sup ->
                email.send(
                    to = sup.email,
                    subject = "New Admin Request awaiting review",
                    body = "User ${user.email} requested Admin coverage of: $label\n" +
                        "Reason: ${dto.reason}"
                )
            }
        return saved
    }

    @Transactional(readOnly = true)
    fun listForUser(userId: Long): List<AdminRequest> =
        adminRequests.findByUserId(userId)

    @Transactional(readOnly = true)
    fun listPending(): List<AdminRequest> =
        adminRequests.findByStatus(RequestStatus.PENDING)
            .sortedBy { it.submittedAt }

    @Transactional
    fun batchApprove(requestIds: List<Long>, approver: User): List<AdminRequest> =
        decide(requestIds, approver, RequestStatus.APPROVED)

    @Transactional
    fun batchReject(requestIds: List<Long>, approver: User): List<AdminRequest> =
        decide(requestIds, approver, RequestStatus.REJECTED)

    private fun decide(
        requestIds: List<Long>,
        approver: User,
        decision: RequestStatus
    ): List<AdminRequest> {
        val now = Instant.now()
        val targets = adminRequests.findAllById(requestIds.distinct())
            .filter { it.status == RequestStatus.PENDING }
        if (targets.isEmpty()) return emptyList()

        val rowsByRequest = roleAssignments
            .findByAdminRequestIdIn(targets.map { it.id })
            .groupBy { it.adminRequest!!.id }

        val results = mutableListOf<AdminRequest>()
        for (req in targets) {
            val updated = adminRequests.save(
                req.copy(status = decision, processedAt = now, processedBy = approver)
            )
            val rows = rowsByRequest[req.id].orEmpty()
            if (decision == RequestStatus.APPROVED) {
                roleAssignments.saveAll(rows.map { it.copy(enabled = true) })
                if (req.user.access.ordinal < AccessLevel.ADMIN.ordinal) {
                    users.save(req.user.copy(access = AccessLevel.ADMIN))
                }
                email.send(
                    to = req.user.email,
                    subject = "You are now an Admin!",
                    body = "Your admin request was approved by ${approver.email}. Visit /admin/dashboard."
                )
            } else {
                email.send(
                    to = req.user.email,
                    subject = "Your admin request was not approved",
                    body = "Your admin request was reviewed and not approved at this time."
                )
            }
            results += updated
        }
        // Approval flipped ADMIN role rows enabled=true. The "who's
        // authorized" cache is stale for the affected zipcodes.
        if (decision == RequestStatus.APPROVED) roleAuthCache.invalidateAuthorizations()
        return results
    }

    fun toDto(req: AdminRequest): AdminRequestDto {
        val rows = roleAssignments.findByAdminRequestId(req.id)
        return AdminRequestDto.from(
            req,
            scopeLevel = scopeOf(rows),
            stateIds = rows.mapNotNull { it.state?.id }.distinct().sorted(),
            regionLabel = regionLabel(rows),
            zipcodes = rows.mapNotNull { it.zipcode }.distinct().sorted()
        )
    }

    private fun scopeOf(rows: List<RoleAssignment>): ScopeLevel =
        rows.firstOrNull()?.scopeLevel ?: ScopeLevel.ZIP

    /** Human-readable purview for emails and the Super review queue. */
    private fun regionLabel(rows: List<RoleAssignment>): String = when (scopeOf(rows)) {
        ScopeLevel.NATIONAL -> "Nationwide"
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
