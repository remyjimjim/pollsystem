package org.kodewerks.pollsystem.adminrequest

import org.kodewerks.pollsystem.email.EmailService
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.AdminRequest
import org.kodewerks.pollsystem.model.RequestStatus
import org.kodewerks.pollsystem.model.RoleAssignment
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.AdminRequestRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
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
    private val email: EmailService
) {

    @Transactional
    fun submit(user: User, dto: SubmitAdminRequest): AdminRequest {
        if (user.access.ordinal < AccessLevel.CREATOR.ordinal) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You must be a Creator before requesting Admin"
            )
        }
        val zipRows = countyZips.findByZipcodeIn(dto.zipcodes.distinct())
        val zipToCounty = zipRows.associateBy { it.zipcode }
        val unknown = dto.zipcodes.distinct().filterNot { it in zipToCounty }
        if (unknown.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown zipcodes: $unknown")
        }

        val saved = adminRequests.save(
            AdminRequest(
                user = user,
                reason = dto.reason,
                status = RequestStatus.PENDING
            )
        )
        val rows = dto.zipcodes.distinct().map { zip ->
            val cz = zipToCounty.getValue(zip)
            RoleAssignment(
                user = user,
                role = AccessLevel.ADMIN,
                state = cz.county.state,
                county = cz.county,
                zipcode = zip,
                enabled = false,
                adminRequest = saved
            )
        }
        roleAssignments.saveAll(rows)

        email.send(
            to = user.email,
            subject = "Your admin request was received",
            body = "Your request to become an Admin is being reviewed. " +
                "We will notify you once a Super reviews it."
        )
        // Notify all Supers. Format the zipcode list as a sorted,
        // comma-joined string so the email body reads as a human list
        // rather than Kotlin's default `[90001, 90012]` toString.
        val zipList = dto.zipcodes.distinct().sorted().joinToString(", ")
        users.findByAccess(AccessLevel.SUPER)
            .filter { it.isEnabled }
            .forEach { sup ->
                email.send(
                    to = sup.email,
                    subject = "New Admin Request awaiting review",
                    body = "User ${user.email} requested Admin access " +
                        "for the following ${dto.zipcodes.distinct().size} zipcode(s): $zipList\n" +
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
        return results
    }

    fun toDto(req: AdminRequest): AdminRequestDto {
        val zips = roleAssignments.findByAdminRequestId(req.id)
            .map { it.zipcode }
            .distinct()
            .sorted()
        return AdminRequestDto.from(req, zips)
    }
}
