package org.kodewerks.pollsystem.superadmin

import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.RequestStatus
import org.kodewerks.pollsystem.repository.CreatorRequestRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.temporal.ChronoUnit

@ConfigurationProperties(prefix = "app.super")
data class SuperProperties(
    /**
     * If an admin's pending-creator-requests share exceeds this fraction of all
     * pending requests, the Super workload table flags them. Default 5%.
     */
    val workloadWarnThreshold: Double = 0.05
)

data class AdminWorkloadRow(
    val adminId: Long,
    val email: String,
    val pending: Long,
    val stale: Long,
    val percentOfTotal: Double,
    val warn: Boolean
)

data class AdminWorkloadDto(
    val totalPending: Long,
    val warnThreshold: Double,
    val rows: List<AdminWorkloadRow>
)

@RestController
@RequestMapping("/api/super")
class SuperWorkloadController(
    private val creatorRequests: CreatorRequestRepository,
    private val users: UserRepository,
    private val props: SuperProperties
) {
    private val staleThresholdHours = 48L

    @GetMapping("/admin-load")
    @Transactional(readOnly = true)
    fun adminLoad(): AdminWorkloadDto {
        val totalPending = creatorRequests.countByStatus(RequestStatus.PENDING)
        if (totalPending == 0L) {
            return AdminWorkloadDto(0, props.workloadWarnThreshold, emptyList())
        }

        // All admins (ADMIN + SUPER) so a Super sees themselves too if they
        // happen to be on a routing path.
        val admins = users.findByAccess(AccessLevel.ADMIN) + users.findByAccess(AccessLevel.SUPER)
        if (admins.isEmpty()) {
            return AdminWorkloadDto(totalPending, props.workloadWarnThreshold, emptyList())
        }

        val staleCutoff = Instant.now().minus(staleThresholdHours, ChronoUnit.HOURS)
        val stale = creatorRequests.findStaleRequests(staleCutoff)
            .filter { it.assignedAdmin != null }
            .groupingBy { it.assignedAdmin!!.id }
            .eachCount()

        val rows = admins.map { admin ->
            val pending = creatorRequests.countByAssignedAdminAndStatus(admin, RequestStatus.PENDING)
            val share = if (totalPending == 0L) 0.0 else pending.toDouble() / totalPending
            AdminWorkloadRow(
                adminId = admin.id,
                email = admin.email,
                pending = pending,
                stale = (stale[admin.id] ?: 0).toLong(),
                percentOfTotal = share,
                warn = share > props.workloadWarnThreshold
            )
        }.sortedByDescending { it.pending }

        return AdminWorkloadDto(
            totalPending = totalPending,
            warnThreshold = props.workloadWarnThreshold,
            rows = rows
        )
    }
}
