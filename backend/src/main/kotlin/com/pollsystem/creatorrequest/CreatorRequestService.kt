package com.pollsystem.creatorrequest

import com.pollsystem.email.EmailService
import com.pollsystem.model.AccessLevel
import com.pollsystem.model.CreatorRequest
import com.pollsystem.model.RequestStatus
import com.pollsystem.model.RoleAssignment
import com.pollsystem.model.User
import com.pollsystem.repository.CountyZipsRepository
import com.pollsystem.repository.CreatorRequestRepository
import com.pollsystem.repository.PollTypeRepository
import com.pollsystem.repository.RoleAssignmentRepository
import com.pollsystem.repository.UserRepository
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
    private val email: EmailService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun submit(user: User, dto: SubmitCreatorRequest): CreatorRequest {
        val pollTypeList = pollTypes.findAllById(dto.pollTypeIds).toList()
        if (pollTypeList.size != dto.pollTypeIds.distinct().size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type")
        }

        val zipRows = countyZips.findByZipcodeIn(dto.zipcodes.distinct())
        val zipToCounty = zipRows.associateBy { it.zipcode }
        val unknown = dto.zipcodes.distinct().filterNot { it in zipToCounty }
        if (unknown.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown zipcodes: $unknown")
        }

        val saved = creatorRequests.save(
            CreatorRequest(
                user = user,
                reason = dto.reason,
                status = RequestStatus.PENDING
            )
        )

        val rows = dto.zipcodes.distinct().flatMap { zip ->
            val cz = zipToCounty.getValue(zip)
            pollTypeList.map { pt ->
                RoleAssignment(
                    user = user,
                    role = AccessLevel.CREATOR,
                    pollType = pt,
                    state = cz.county.state,
                    county = cz.county,
                    zipcode = zip,
                    enabled = false,
                    creatorRequest = saved
                )
            }
        }
        roleAssignments.saveAll(rows)

        val assigned = routeToAdmin(dto.zipcodes.distinct())
        val withAdmin = if (assigned != null) {
            creatorRequests.save(saved.copy(assignedAdmin = assigned))
        } else {
            log.warn("No admin found for creator request {} zips={}", saved.id, dto.zipcodes)
            saved
        }

        email.send(
            to = user.email,
            subject = "Your creator request was received",
            body = "Your request to become a Creator is being processed. " +
                "We will notify you once it is reviewed."
        )
        if (assigned != null) {
            email.send(
                to = assigned.email,
                subject = "New Creator Request awaiting review",
                body = "User ${user.email} requested Creator access for zipcodes ${dto.zipcodes}.\n" +
                    "Reason: ${dto.reason}"
            )
        }

        return withAdmin
    }

    private fun routeToAdmin(zipcodes: List<String>): User? {
        val candidates = roleAssignments
            .findEnabledByRoleAndZipcodes(AccessLevel.ADMIN, zipcodes)
            .map { it.user }
            .filter { it.isEnabled }
            .distinctBy { it.id }
        if (candidates.isEmpty()) return null
        return candidates.minBy {
            creatorRequests.countByAssignedAdminAndStatus(it, RequestStatus.PENDING)
        }
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
        val targets = creatorRequests.findAllById(requestIds.distinct())
            .filter { it.status == RequestStatus.PENDING }
        if (targets.isEmpty()) return emptyList()

        val rowsByRequest = roleAssignments
            .findByCreatorRequestIdIn(targets.map { it.id })
            .groupBy { it.creatorRequest!!.id }

        val results = mutableListOf<CreatorRequest>()
        for (req in targets) {
            val updated = creatorRequests.save(
                req.copy(status = decision, processedAt = now, processedBy = decidedBy)
            )
            val rows = rowsByRequest[req.id].orEmpty()
            if (decision == RequestStatus.APPROVED) {
                roleAssignments.saveAll(rows.map { it.copy(enabled = true) })
                if (req.user.access.ordinal < AccessLevel.CREATOR.ordinal) {
                    users.save(req.user.copy(access = AccessLevel.CREATOR))
                }
                email.send(
                    to = req.user.email,
                    subject = "You are now a Creator!",
                    body = "Your creator request was approved. Visit /home to start creating polls."
                )
            } else {
                // role assignments stay enabled=false; leave them as audit trail
                email.send(
                    to = req.user.email,
                    subject = "Your creator request was not approved",
                    body = "Your creator request was reviewed and not approved at this time."
                )
            }
            results += updated
        }
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
        val zips = rows.map { it.zipcode }.distinct().sorted()
        val pollTypeIds = rows.mapNotNull { it.pollType?.id }.distinct().sorted()
        return CreatorRequestDto.from(req, zips, pollTypeIds)
    }
}
